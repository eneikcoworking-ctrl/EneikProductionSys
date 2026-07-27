<script lang="ts">
  import { toastsStore, removeToast } from '../lib/toastStore';
</script>

<div class="toast-container" aria-live="polite">
  {#each $toastsStore as toast (toast.id)}
    <div class="toast-item {toast.type}">
      <span class="icon">
        {#if toast.type === 'success'}✅
        {:else if toast.type === 'error'}❌
        {:else if toast.type === 'warning'}⚠️
        {:else}ℹ️{/if}
      </span>
      <span class="message">{toast.message}</span>
      <button class="btn-close" onclick={() => removeToast(toast.id)}>×</button>
    </div>
  {/each}
</div>

<style>
  .toast-container {
    position: fixed;
    bottom: 1.5rem;
    right: 1.5rem;
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
    z-index: 9999;
    max-width: 400px;
    pointer-events: none;
  }

  .toast-item {
    pointer-events: auto;
    display: flex;
    align-items: center;
    gap: 0.75rem;
    background: #1e293b;
    border: 1px solid #334155;
    border-radius: 8px;
    padding: 0.85rem 1.1rem;
    color: #f8fafc;
    box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.5);
    font-size: 0.875rem;
    animation: slideIn 0.25s ease-out;
  }

  .toast-item.success { border-left: 4px solid #10b981; }
  .toast-item.error { border-left: 4px solid #ef4444; }
  .toast-item.warning { border-left: 4px solid #f59e0b; }
  .toast-item.info { border-left: 4px solid #3b82f6; }

  .message {
    flex: 1;
    line-height: 1.35;
  }

  .btn-close {
    background: none;
    border: none;
    color: #94a3b8;
    font-size: 1.2rem;
    cursor: pointer;
    padding: 0 0.25rem;
  }
  .btn-close:hover { color: #fff; }

  @keyframes slideIn {
    from {
      transform: translateY(20px);
      opacity: 0;
    }
    to {
      transform: translateY(0);
      opacity: 1;
    }
  }
</style>
