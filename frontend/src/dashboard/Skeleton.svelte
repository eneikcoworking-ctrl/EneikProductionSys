<script lang="ts">
  // Shimmer placeholder sized to match the real content it stands in for, so nothing shifts
  // layout once real data arrives (zero Cumulative Layout Shift) - added directly in response to
  // BARCAN-TAG-11's perceptual-timing critique (Churchland: sub-100ms illusion of continuity).
  export let height = '20px';
  export let width = '100%';
  export let rounded = 'var(--radius)';
</script>

<div class="skeleton" style="height:{height}; width:{width}; border-radius:{rounded};"></div>

<style>
  .skeleton {
    background: linear-gradient(90deg, var(--neutral-200) 25%, var(--neutral-100) 37%, var(--neutral-200) 63%);
    background-size: 400% 100%;
    animation: shimmer 1.4s ease infinite;
  }
  @keyframes shimmer {
    0% { background-position: 100% 50%; }
    100% { background-position: 0 50%; }
  }
  @media (prefers-reduced-motion: reduce) {
    .skeleton { animation: none; background: var(--neutral-200); }
  }
</style>
