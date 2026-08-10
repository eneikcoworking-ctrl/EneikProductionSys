<script lang="ts">
  // The Forge (2026-08-10) - engineer-facing instrument view, companion to Роща/ProductTree in the
  // same "Verdant Flow" visual family (see forge-theme.css, tokens copied from .product-tree, not
  // approximated). Replaces the old TocSentinelView + SixSigmaKaizenPanel tabs.
  //
  // Hard rule (operator, 2026-08-09): "я не хочу никогда одновременно видеть данные по заводу и по
  // продукту - это разные миры разные контексты". Enforced structurally, not just visually - exactly
  // one room component is ever mounted at a time via this {#if} chain; there is no shared state or
  // shared fetch between rooms, each room fetches only its own scope.
  import ForgeFactoryRoom from './ForgeFactoryRoom.svelte';
  import ForgeDeliveryRoom from './ForgeDeliveryRoom.svelte';
  import ForgeProductRoom from './ForgeProductRoom.svelte';
  import './forge-theme.css';

  export let projectId: string;

  let room: 'factory' | 'delivery' | 'product' = 'factory';
</script>

<div class="forge-room">
  <div class="forge-head">
    <div>
      <span class="forge-eyebrow">Precision instruments, one room at a time</span>
      <h2>The Forge</h2>
    </div>
  </div>

  <div class="room-doors" role="tablist" aria-label="Forge room">
    <button role="tab" aria-selected={room === 'factory'} class:active={room === 'factory'} onclick={() => (room = 'factory')}>
      <span class="door-glyph">🕸️</span> Factory
    </button>
    <button role="tab" aria-selected={room === 'delivery'} class:active={room === 'delivery'} onclick={() => (room = 'delivery')}>
      <span class="door-glyph">🌳</span> Delivery
    </button>
    <button role="tab" aria-selected={room === 'product'} class:active={room === 'product'} onclick={() => (room = 'product')}>
      <span class="door-glyph">🌸</span> Product
    </button>
  </div>

  {#if room === 'factory'}
    <ForgeFactoryRoom />
  {:else if room === 'delivery'}
    <ForgeDeliveryRoom {projectId} />
  {:else}
    <ForgeProductRoom {projectId} />
  {/if}
</div>

<style>
  .forge-head {
    align-items: center;
    display: flex;
    justify-content: space-between;
  }

  .forge-head h2 {
    font-family: var(--font-display);
    font-size: 26px;
    margin: 2px 0 0;
  }

  .room-doors {
    display: flex;
    gap: 8px;
  }

  .room-doors button {
    background: var(--forge-surface);
    border: 1px solid var(--forge-line-soft);
    border-radius: var(--radius-lg);
    color: var(--forge-ink-muted);
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    font-weight: 600;
    padding: 10px 16px;
  }

  .door-glyph {
    font-size: 14px;
  }

  .room-doors button.active {
    background: var(--forge-gold-soft);
    border-color: var(--forge-gold);
    color: var(--forge-ink);
  }
</style>
