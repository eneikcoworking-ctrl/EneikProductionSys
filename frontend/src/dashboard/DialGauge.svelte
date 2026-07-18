<script lang="ts">
  export let label: string;
  export let percent: number; // 0-100
  export let tone: 'primary' | 'warning' | 'accent' | 'error' = 'primary';
  export let sublabel: string = '';

  $: clamped = Math.max(0, Math.min(100, percent));
</script>

<div class="dial dial-{tone}">
  <div class="ring" style="--pct: {clamped}">
    <div class="ring-hole">
      <span class="pct">{Math.round(clamped)}%</span>
    </div>
  </div>
  <div class="labels">
    <span class="label">{label}</span>
    {#if sublabel}<span class="sublabel">{sublabel}</span>{/if}
  </div>
</div>

<style>
  .dial {
    align-items: center;
    display: flex;
    gap: var(--space-3);
  }
  .ring {
    --size: 72px;
    --track: var(--neutral-200);
    background: conic-gradient(var(--tone-color, var(--primary)) calc(var(--pct) * 1%), var(--track) 0);
    border-radius: 50%;
    flex: none;
    height: var(--size);
    width: var(--size);
    display: flex;
    align-items: center;
    justify-content: center;
    position: relative;
  }
  .ring::after {
    background: var(--surface);
    border-radius: 50%;
    content: '';
    height: 76%;
    position: absolute;
    width: 76%;
  }
  .ring-hole {
    position: relative;
    z-index: 1;
  }
  .pct {
    font-family: var(--font-mono);
    font-size: 14px;
    font-weight: 700;
  }
  .dial-primary { --tone-color: var(--primary); }
  .dial-warning { --tone-color: var(--warning); }
  .dial-accent { --tone-color: var(--accent); }
  .dial-error { --tone-color: var(--error); }
  .dial-primary .pct { color: var(--primary); }
  .dial-warning .pct { color: var(--warning); }
  .dial-accent .pct { color: var(--accent); }
  .dial-error .pct { color: var(--error); }

  .labels {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
  }
  .label {
    font-family: var(--font-mono);
    font-size: 12px;
    font-weight: 700;
    letter-spacing: 0.06em;
    text-transform: uppercase;
    color: var(--neutral-800);
  }
  .sublabel {
    color: var(--neutral-500);
    font-size: 11px;
  }
</style>
