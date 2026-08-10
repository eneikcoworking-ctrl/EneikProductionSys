<script lang="ts">
  // Shared brass dial gauge (2026-08-10, Кузница) - one real instrument reused by all three rooms,
  // per the Stitch reference: the same mechanical family in each room, only the reading differs.
  // Needle angle is the only thing driven by real data; the dial face itself never changes shape.
  export let value: number; // 0..max
  export let max: number = 6;
  export let label: string;
  export let sublabel: string = '';
  export let tone: 'healthy' | 'attention' | 'critical' = 'healthy';
  export let size: number = 180;

  const SWEEP_DEG = 240; // -120deg to +120deg, matching a real analog gauge face
  $: clamped = Math.max(0, Math.min(max, value));
  $: needleDeg = -120 + (clamped / max) * SWEEP_DEG;
  $: ticks = Array.from({ length: 7 }, (_, i) => -120 + (i / 6) * SWEEP_DEG);
</script>

<div class="gauge-dial" style="width: {size}px">
  <svg viewBox="0 0 200 200" role="img" aria-label="{label}: {clamped.toFixed(2)} of {max}">
    <circle cx="100" cy="100" r="92" class="dial-rim" />
    <circle cx="100" cy="100" r="80" class="dial-face" />
    {#each ticks as deg}
      <line x1="100" y1="24" x2="100" y2="34" class="dial-tick" transform="rotate({deg} 100 100)" />
    {/each}
    <line x1="100" y1="106" x2="100" y2="38" class="dial-needle needle-{tone}" transform="rotate({needleDeg} 100 100)" />
    <circle cx="100" cy="100" r="7" class="dial-hub" />
  </svg>
  <div class="gauge-caption">
    <strong>{label}</strong>
    {#if sublabel}<span>{sublabel}</span>{/if}
  </div>
</div>

<style>
  .gauge-dial {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
  }

  .dial-rim {
    fill: var(--forge-gold-soft);
    stroke: var(--forge-gold);
    stroke-width: 4;
  }

  .dial-face {
    fill: var(--forge-bg);
    stroke: var(--forge-line-soft);
    stroke-width: 1;
  }

  .dial-tick {
    stroke: var(--forge-ink-muted);
    stroke-width: 2;
  }

  .dial-needle {
    stroke-linecap: round;
    stroke-width: 3.5;
    transition: transform 0.6s cubic-bezier(0.22, 0.61, 0.36, 1);
  }

  .needle-healthy { stroke: var(--forge-healthy); }
  .needle-attention { stroke: var(--forge-attention); }
  .needle-critical { stroke: var(--forge-critical); }

  .dial-hub {
    fill: var(--forge-gold);
    stroke: var(--forge-surface);
    stroke-width: 1.5;
  }

  .gauge-caption {
    display: flex;
    flex-direction: column;
    align-items: center;
    font-size: 12px;
    text-align: center;
  }

  .gauge-caption strong {
    color: var(--forge-ink);
    font-family: var(--font-display);
    font-size: 14px;
  }

  .gauge-caption span {
    color: var(--forge-ink-muted);
  }
</style>
