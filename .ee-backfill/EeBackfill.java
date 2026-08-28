import com.eneik.production.services.EpistemicMetadataClassifier;
import com.eneik.production.services.FeatureService;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * One-off backfill of epistemic_score / epistemic_layer for features minted before the E3 formula was
 * restored (2026-08-27, ENGINEERING_PHILOSOPHY_ACTION_PLAN.md Phase 1).
 *
 * <p>Calls the SHIPPED FeatureService and EpistemicMetadataClassifier rather than reimplementing the
 * formula: a second implementation would be a second source of truth, and the two would drift.
 *
 * <p>Where the evidence comes from, and why not from the feature row itself: measured 2026-08-27, all 211
 * features in this database carry root_wishlist_id = NULL, jtbd = NULL, and a synthetic title of the
 * literal form "Feature &lt;uuid&gt;". Scoring them from their own row yields 15.0 for every one of them -
 * a NEW constant, i.e. the same degeneracy this phase exists to remove, wearing a different number. The
 * content survived one level down: every one of the 211 has tasks, tasks carry cynefin_domain (926 of
 * 1765 populated), title and description, and source_wishlist_id links back to the wishlist that still
 * holds jtbd / six_sigma_metric / toc_constraint_ref. This walks that chain instead of inventing numbers.
 *
 * <p>Only rows with a NULL epistemic_score are touched, so it is idempotent and can never overwrite a
 * score the running factory computed itself. Pass "apply" to write; default is a dry run.
 */
public class EeBackfill {

    private static final int MAX_FRAGMENT = 2000;
    private static final int MAX_TEXT = 20000;

    /** Same scale FeatureService uses, so "most conservative" here means the same thing it means there. */
    private static int cynefinRank(String domain) {
        return switch (domain == null ? "" : domain.toLowerCase(Locale.ROOT).trim()) {
            case "simple", "clear" -> 4;
            case "complicated" -> 3;
            case "complex" -> 2;
            case "chaotic" -> 1;
            default -> 0;
        };
    }

    private static final class Evidence {
        final StringBuilder text = new StringBuilder();
        final Map<String, Integer> cynefinVotes = new HashMap<>();
        String jtbd;
        String sixSigma;
        String toc;

        void addText(String fragment) {
            if (fragment == null || fragment.isBlank() || text.length() >= MAX_TEXT) {
                return;
            }
            String trimmed = fragment.length() > MAX_FRAGMENT ? fragment.substring(0, MAX_FRAGMENT) : fragment;
            text.append(' ').append(trimmed);
        }

        void voteCynefin(String domain) {
            if (domain != null && !domain.isBlank()) {
                cynefinVotes.merge(domain.toLowerCase(Locale.ROOT).trim(), 1, Integer::sum);
            }
        }

        /** Modal domain across this feature's tasks; ties break toward the LESS understood domain. */
        String resolvedCynefin() {
            String best = null;
            int bestVotes = -1;
            for (Map.Entry<String, Integer> vote : cynefinVotes.entrySet()) {
                if (vote.getValue() > bestVotes
                        || (vote.getValue() == bestVotes && cynefinRank(vote.getKey()) < cynefinRank(best))) {
                    best = vote.getKey();
                    bestVotes = vote.getValue();
                }
            }
            return best;
        }
    }

    public static void main(String[] args) throws Exception {
        boolean apply = args.length > 0 && "apply".equalsIgnoreCase(args[0]);
        String url = System.getProperty("db.url", "jdbc:h2:/data/eneik_db");

        FeatureService formula = new FeatureService(null, null);
        EpistemicMetadataClassifier classifier = new EpistemicMetadataClassifier();

        try (Connection conn = DriverManager.getConnection(url, "sa", "")) {

            List<String> targets = new ArrayList<>();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT ID FROM FEATURES WHERE EPISTEMIC_SCORE IS NULL")) {
                while (rs.next()) {
                    targets.add(rs.getString("ID"));
                }
            }
            System.out.println("features with NULL score: " + targets.size());

            Map<String, Evidence> evidenceByFeature = new HashMap<>();
            String gather =
                    "SELECT t.FEATURE_ID AS FID, t.CYNEFIN_DOMAIN AS T_CYN, t.TITLE AS T_TITLE, "
                  + "       t.DESCRIPTION AS T_DESC, w.CONTENT AS W_CONTENT, w.GROUNDED_CONTENT AS W_GROUNDED, "
                  + "       w.JTBD AS W_JTBD, w.ACCEPTANCE_CRITERIA AS W_AC, w.CYNEFIN_DOMAIN AS W_CYN, "
                  + "       w.SIX_SIGMA_METRIC AS W_SIX, w.TOC_CONSTRAINT_REF AS W_TOC "
                  + "FROM TASKS t LEFT JOIN WISHLIST w ON t.SOURCE_WISHLIST_ID = w.ID "
                  + "WHERE t.FEATURE_ID IS NOT NULL";
            int taskRows = 0;
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(gather)) {
                while (rs.next()) {
                    taskRows++;
                    Evidence ev = evidenceByFeature.computeIfAbsent(rs.getString("FID"), k -> new Evidence());
                    ev.voteCynefin(rs.getString("T_CYN"));
                    ev.voteCynefin(rs.getString("W_CYN"));
                    ev.addText(rs.getString("T_TITLE"));
                    ev.addText(rs.getString("T_DESC"));
                    ev.addText(rs.getString("W_CONTENT"));
                    ev.addText(rs.getString("W_GROUNDED"));
                    ev.addText(rs.getString("W_JTBD"));
                    ev.addText(rs.getString("W_AC"));
                    if (ev.jtbd == null) ev.jtbd = blankToNull(rs.getString("W_JTBD"));
                    if (ev.sixSigma == null) ev.sixSigma = blankToNull(rs.getString("W_SIX"));
                    if (ev.toc == null) ev.toc = blankToNull(rs.getString("W_TOC"));
                }
            }
            System.out.println("task rows gathered: " + taskRows
                    + " covering " + evidenceByFeature.size() + " features");

            Map<String, Integer> layerCounts = new LinkedHashMap<>();
            Map<Double, Integer> scoreCounts = new TreeMap<>();
            Map<String, Integer> cynefinResolved = new TreeMap<>();
            Map<String, Integer> kanoResolved = new TreeMap<>();
            int noEvidence = 0;
            int written = 0;

            String update = "UPDATE FEATURES SET EPISTEMIC_SCORE = ?, EPISTEMIC_LAYER = ?, "
                          + "KANO_CLASS = COALESCE(NULLIF(KANO_CLASS, ''), ?), "
                          + "CYNEFIN_DOMAIN = COALESCE(NULLIF(CYNEFIN_DOMAIN, ''), ?) WHERE ID = ?";

            try (PreparedStatement ps = conn.prepareStatement(update)) {
                for (String featureId : targets) {
                    Evidence ev = evidenceByFeature.get(featureId);
                    String cynefin = null;
                    String kano = null;
                    double ems = 0.0;
                    if (ev == null) {
                        noEvidence++;
                    } else {
                        cynefin = ev.resolvedCynefin();
                        kano = classifier.classify(ev.text.toString()).kanoClass();
                        ems = formula.emsContractConformance(ev.jtbd, ev.sixSigma, ev.toc);
                    }

                    double score = formula.calculateEpistemicEntrenchment(kano, cynefin, ems);
                    String layer = formula.classifyEpistemicLayer(score);

                    layerCounts.merge(layer, 1, Integer::sum);
                    scoreCounts.merge(score, 1, Integer::sum);
                    cynefinResolved.merge(String.valueOf(cynefin), 1, Integer::sum);
                    kanoResolved.merge(String.valueOf(kano), 1, Integer::sum);

                    if (apply) {
                        ps.setDouble(1, score);
                        ps.setString(2, layer);
                        ps.setString(3, kano);
                        ps.setString(4, cynefin);
                        ps.setString(5, featureId);
                        written += ps.executeUpdate();
                    }
                }
            }

            System.out.println("=== EE BACKFILL " + (apply ? "APPLY" : "DRY RUN") + " ===");
            System.out.println("features with no task evidence at all: " + noEvidence);
            System.out.println("rows written: " + written);
            print("layer distribution", layerCounts);
            print("resolved cynefin", cynefinResolved);
            print("resolved kano", kanoResolved);
            System.out.println("--- score distribution ---");
            scoreCounts.forEach((k, v) -> System.out.println("  " + k + " -> " + v));
            System.out.println("distinct scores: " + scoreCounts.size());
        }
    }

    private static <K> void print(String label, Map<K, Integer> counts) {
        System.out.println("--- " + label + " ---");
        counts.forEach((k, v) -> System.out.println("  " + k + ": " + v));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
