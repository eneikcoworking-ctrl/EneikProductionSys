<script lang="ts">
  import { onMount } from 'svelte';

  export let projectId: string;

  let deliveryData: any = null;
  let loading = true;
  let error: string | null = null;
  let showAcceptConfirm = false;
  let acceptConfirmationText = '';
  let acceptingProject = false;

  $: acceptConfirmationMatches = acceptConfirmationText.trim() === 'ACCEPT';

  async function fetchDelivery() {
    try {
      const baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
      const response = await fetch(`${baseUrl}/api/projects/${projectId}/client-delivery`);
      if (!response.ok) throw new Error('Failed to fetch delivery data');
      deliveryData = await response.json();
    } catch (e: any) {
      error = e.message;
    } finally {
      loading = false;
    }
  }

  async function acceptProject() {
    if (!acceptConfirmationMatches) return;
    acceptingProject = true;
    try {
      const baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
      const response = await fetch(`${baseUrl}/api/projects/${projectId}/accept`, { method: 'POST' });
      if (!response.ok) throw new Error('Failed to accept project');
      window.location.reload();
    } catch (e: any) {
      alert('Error: ' + e.message);
    } finally {
      acceptingProject = false;
    }
  }

  function openAcceptConfirm() {
    acceptConfirmationText = '';
    showAcceptConfirm = true;
  }

  function closeAcceptConfirm() {
    if (acceptingProject) return;
    showAcceptConfirm = false;
    acceptConfirmationText = '';
  }

  onMount(fetchDelivery);
</script>

<div class="delivery-shell">
  <header class="delivery-header">
    <h1>Client Delivery View</h1>
  </header>

  {#if loading}
    <div class="center-state">Loading delivery artifacts...</div>
  {:else if error}
    <div class="error-state">{error}</div>
  {:else if deliveryData}
    <section class="summary-card">
      <h2>Quality Summary</h2>
      <p>{deliveryData.testSummary}</p>
    </section>

    <div class="delivery-grid">
      <section>
        <h2>Requested (Client Wishlist)</h2>
        <ul class="item-list">
          {#each deliveryData.requested as item}
            <li>{item.content || item.text}</li>
          {:else}
            <li class="empty">No client requests found.</li>
          {/each}
        </ul>
      </section>

      <section>
        <h2>Delivered Features</h2>
        <ul class="item-list delivered">
          {#each deliveryData.delivered as task}
            <li>
              <div class="task-title">{task.title || task.TITLE || 'Delivered Slice'}</div>
              <div class="task-dod">{task.description || task.DESCRIPTION}</div>
              {#if task.payload && task.payload.definitionOfDone}
                <div class="task-dod">DoD: {task.payload.definitionOfDone}</div>
              {/if}
            </li>
          {:else}
            <li class="empty">No completed tasks yet.</li>
          {/each}
        </ul>
      </section>
    </div>

    <div class="delivery-grid">
      <section>
        <h2>Pull Requests</h2>
        <ul class="link-list">
          {#each deliveryData.prLinks as link}
            <li>
              <a href={link} target="_blank" rel="noreferrer">{link}</a>
            </li>
          {:else}
            <li class="empty">No PR links available.</li>
          {/each}
        </ul>
      </section>

      <section>
        <h2>Screenshots</h2>
        <div class="screenshots">
          {#each deliveryData.screenshots as url}
            <a href={url} target="_blank" rel="noreferrer">
              <img src={url} alt="Delivery screenshot" />
            </a>
          {:else}
            <p class="empty">No screenshots available.</p>
          {/each}
        </div>
      </section>
    </div>

    <section class="final-delivery-zone">
      <div>
        <h2>Final Delivery Acceptance</h2>
        <p>This is the final project action. Use it only after reviewing delivery artifacts, PRs, screenshots, and quality evidence.</p>
      </div>
      <button type="button" onclick={openAcceptConfirm}>
        Open Final Acceptance
      </button>
    </section>
  {/if}
</div>

{#if showAcceptConfirm}
  <div class="accept-backdrop">
    <section class="accept-dialog" role="dialog" aria-modal="true" aria-labelledby="client-accept-title">
      <h2 id="client-accept-title">Accept and Close Project</h2>
      <p>
        Acceptance stops further generation for this project. Type <code>ACCEPT</code> to confirm.
      </p>
      <input
        type="text"
        bind:value={acceptConfirmationText}
        placeholder="ACCEPT"
        disabled={acceptingProject}
      />
      <div class="accept-actions">
        <button type="button" class="cancel" onclick={closeAcceptConfirm} disabled={acceptingProject}>Cancel</button>
        <button type="button" class="danger" onclick={acceptProject} disabled={!acceptConfirmationMatches || acceptingProject}>
          {acceptingProject ? 'Accepting...' : 'Accept and Close'}
        </button>
      </div>
    </section>
  </div>
{/if}

<style>
  .delivery-shell {
    display: flex;
    flex-direction: column;
    gap: 24px;
    margin: 0 auto;
    max-width: 1024px;
    padding: 24px;
  }
  .delivery-header {
    align-items: center;
    display: flex;
    justify-content: space-between;
  }
  .delivery-header h1 {
    color: #0f172a;
    font-size: 30px;
    font-weight: 800;
    margin: 0;
  }
  .center-state,
  .error-state {
    border-radius: 8px;
    padding: 32px;
    text-align: center;
  }
  .center-state {
    color: #64748b;
  }
  .error-state {
    background: #fee2e2;
    color: #991b1b;
  }
  .summary-card {
    background: #eff6ff;
    border: 1px solid #bfdbfe;
    border-radius: 8px;
    padding: 20px;
  }
  .summary-card h2,
  .delivery-grid h2 {
    color: #1e3a8a;
    font-size: 18px;
    font-weight: 800;
    margin: 0 0 10px;
  }
  .summary-card p {
    color: #1d4ed8;
    margin: 0;
  }
  .delivery-grid {
    display: grid;
    gap: 24px;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .item-list,
  .link-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
    list-style: none;
    margin: 0;
    padding: 0;
  }
  .item-list li,
  .link-list li {
    background: white;
    border: 1px solid #e2e8f0;
    border-radius: 8px;
    box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
    color: #0f172a;
    padding: 12px;
  }
  .item-list.delivered li {
    border-color: #bbf7d0;
  }
  .task-title {
    font-weight: 700;
  }
  .task-dod {
    color: #64748b;
    font-size: 12px;
    margin-top: 4px;
  }
  .link-list a {
    color: #2563eb;
    overflow-wrap: anywhere;
  }
  .screenshots {
    display: grid;
    gap: 12px;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .screenshots a {
    border: 1px solid #cbd5e1;
    border-radius: 8px;
    display: block;
    overflow: hidden;
  }
  .screenshots img {
    aspect-ratio: 16 / 9;
    display: block;
    height: auto;
    object-fit: cover;
    width: 100%;
  }
  .empty {
    color: #64748b;
    font-style: italic;
  }
  .final-delivery-zone {
    align-items: center;
    background: #fff7f7;
    border: 1px solid #fecaca;
    border-radius: 8px;
    display: flex;
    gap: 16px;
    justify-content: space-between;
    padding: 16px;
  }
  .final-delivery-zone h2 {
    color: #991b1b;
    font-size: 14px;
    font-weight: 800;
    margin: 0 0 4px;
    text-transform: uppercase;
  }
  .final-delivery-zone p {
    color: #475569;
    font-size: 13px;
    line-height: 1.45;
    margin: 0;
  }
  .final-delivery-zone button {
    border: 1px solid #dc2626;
    border-radius: 8px;
    color: #b91c1c;
    font-weight: 700;
    min-height: 38px;
    padding: 0 14px;
    white-space: nowrap;
  }
  .accept-backdrop {
    align-items: center;
    background: rgba(15, 23, 42, 0.55);
    display: flex;
    inset: 0;
    justify-content: center;
    padding: 16px;
    position: fixed;
    z-index: 80;
  }
  .accept-dialog {
    background: white;
    border-radius: 12px;
    box-shadow: 0 24px 70px rgba(15, 23, 42, 0.28);
    max-width: 460px;
    padding: 20px;
    width: 100%;
  }
  .accept-dialog h2 {
    color: #0f172a;
    font-size: 20px;
    font-weight: 800;
    margin: 0 0 8px;
  }
  .accept-dialog p {
    color: #475569;
    font-size: 14px;
    line-height: 1.45;
    margin: 0 0 14px;
  }
  .accept-dialog input {
    border: 1px solid #cbd5e1;
    border-radius: 8px;
    height: 40px;
    padding: 0 12px;
    width: 100%;
  }
  .accept-dialog input:focus {
    border-color: #dc2626;
    box-shadow: 0 0 0 3px rgba(220, 38, 38, 0.12);
    outline: none;
  }
  .accept-actions {
    display: flex;
    gap: 12px;
    justify-content: flex-end;
    margin-top: 18px;
  }
  .accept-actions button {
    border-radius: 8px;
    font-weight: 700;
    min-height: 36px;
    padding: 0 14px;
  }
  .accept-actions .cancel {
    background: #f8fafc;
    border: 1px solid #cbd5e1;
    color: #334155;
  }
  .accept-actions .danger {
    background: #dc2626;
    color: white;
  }
  .accept-actions button:disabled {
    cursor: not-allowed;
    opacity: 0.55;
  }
  @media (max-width: 760px) {
    .delivery-grid,
    .screenshots {
      grid-template-columns: 1fr;
    }
    .final-delivery-zone {
      align-items: flex-start;
      flex-direction: column;
    }
  }
</style>
