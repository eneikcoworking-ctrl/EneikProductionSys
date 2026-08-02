<script lang="ts">
  // Planting a wish = planting a seed at the trunk's base. Submits to the same
  // POST /api/projects/{projectId}/wishlist endpoint the old dashboard used - no backend change.
  import { fade, scale } from 'svelte/transition';
  import { API_BASE } from '../../lib/api';

  export let projectId: string;
  export let onPlanted: () => void;

  let open = false;
  let text = '';
  let submitting = false;
  let statusMsg = '';
  // Ceremony state: 'sowing' while the real request is in flight, 'planted' for a brief real
  // confirmation once it succeeds. The dismiss delay after 'planted' is a UI transition duration
  // (like a toast), not a fabricated progress bar - it never runs before the real POST resolves.
  let ceremony: 'idle' | 'sowing' | 'planted' = 'idle';

  async function plant() {
    if (!text.trim()) return;
    submitting = true;
    ceremony = 'sowing';
    statusMsg = '';
    try {
      const res = await fetch(`${API_BASE}/api/projects/${projectId}/wishlist`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ projectId, source: 'client', content: text })
      });
      if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        statusMsg = err.error ?? 'Failed to plant the seed';
        ceremony = 'idle';
        return;
      }
      text = '';
      open = false;
      onPlanted();
      ceremony = 'planted';
      setTimeout(() => { ceremony = 'idle'; }, 1100);
    } catch (e: any) {
      statusMsg = e.message ?? 'Failed to plant the seed';
      ceremony = 'idle';
    } finally {
      submitting = false;
    }
  }
</script>

<div class="seed-planter">
  {#if !open}
    <button class="plant-toggle" onclick={() => (open = true)}>+ Plant a wish</button>
  {:else}
    <div class="plant-form">
      <textarea
        bind:value={text}
        placeholder="What should the product do next?"
        rows="3"
      ></textarea>
      <div class="plant-actions">
        <button class="btn-cancel" onclick={() => { open = false; text = ''; statusMsg = ''; }}>Cancel</button>
        <button class="btn-plant" onclick={plant} disabled={submitting || !text.trim()}>
          {submitting ? 'Planting…' : 'Plant'}
        </button>
      </div>
      {#if statusMsg}
        <p class="plant-status">{statusMsg}</p>
      {/if}
    </div>
  {/if}
</div>

{#if ceremony !== 'idle'}
  <div class="ceremony-overlay" transition:fade={{ duration: 200 }}>
    <div class="ceremony-content" transition:scale={{ duration: 300, start: 0.85 }}>
      {#if ceremony === 'sowing'}
        <span class="ceremony-icon sowing">🌱</span>
        <p>Planting your wish…</p>
      {:else}
        <span class="ceremony-icon planted">🌿</span>
        <p>Rooted. It'll surface as a branch once compiled.</p>
      {/if}
    </div>
  </div>
{/if}

<style>
  .seed-planter {
    display: flex;
    justify-content: center;
  }

  .plant-toggle {
    background: var(--surface);
    border: 1px dashed var(--neutral-300);
    color: var(--neutral-700);
    font-weight: 600;
  }

  .plant-toggle:hover {
    border-color: var(--tree-healthy, var(--primary));
    color: var(--tree-healthy, var(--primary));
  }

  .plant-form {
    background: var(--surface);
    border: 1px solid var(--neutral-200);
    border-radius: var(--radius-lg);
    display: grid;
    gap: 10px;
    max-width: 480px;
    padding: 14px;
    width: 100%;
  }

  .plant-actions {
    display: flex;
    gap: 8px;
    justify-content: flex-end;
  }

  .btn-cancel {
    background: var(--surface);
    border: 1px solid var(--neutral-300);
    color: var(--neutral-700);
  }

  .plant-status {
    color: var(--error);
    font-size: 13px;
    margin: 0;
  }

  .ceremony-overlay {
    align-items: center;
    background: rgba(11, 28, 48, 0.4);
    display: flex;
    inset: 0;
    justify-content: center;
    position: fixed;
    z-index: 50;
  }

  .ceremony-content {
    background: var(--surface);
    border-radius: var(--radius-lg);
    box-shadow: var(--shadow);
    display: grid;
    gap: 12px;
    justify-items: center;
    padding: 36px 44px;
    text-align: center;
  }

  .ceremony-icon {
    font-size: 40px;
  }

  .ceremony-icon.sowing {
    animation: sway 1.2s ease-in-out infinite;
  }

  .ceremony-content p {
    color: var(--neutral-700);
    font-size: 14px;
    margin: 0;
    max-width: 220px;
  }

  @keyframes sway {
    0%, 100% { transform: rotate(-6deg); }
    50% { transform: rotate(6deg); }
  }

  @media (prefers-reduced-motion: reduce) {
    .ceremony-icon.sowing { animation: none; }
  }
</style>
