<script lang="ts">
  import { onMount } from 'svelte';
  import type { AgentAccount, AgentSnapshot, AgentTask, AgentTaskStatus } from '../lib/types';

  const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

  const initialSnapshot: AgentSnapshot = {
    accounts: [],
    tasks: [],
    summary: {},
  };

  let snapshot = $state<AgentSnapshot>(initialSnapshot);
  let title = $state('Build agent account workflow');
  let description = $state('Create roles, accounts, task queue, auto-claim rules, backend API, database schema, and frontend dashboard.');
  let isLoading = $state(false);
  let isSubmitting = $state(false);
  let errorMessage = $state<string | null>(null);
  let statusFilter = $state<'ALL' | AgentTaskStatus>('ALL');

  const statusOptions: Array<'ALL' | AgentTaskStatus> = ['ALL', 'TODO', 'CLAIMED', 'IN_PROGRESS', 'REVIEW', 'DONE', 'BLOCKED'];

  let filteredTasks = $derived(
    statusFilter === 'ALL'
      ? snapshot.tasks
      : snapshot.tasks.filter((task) => task.status === statusFilter)
  );

  async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
    const response = await fetch(`${API_BASE_URL}${path}`, {
      headers: {
        'Content-Type': 'application/json',
        ...(options.headers ?? {}),
      },
      ...options,
    });

    if (!response.ok) {
      const text = await response.text();
      throw new Error(text || `${response.status} ${response.statusText}`);
    }

    return response.json() as Promise<T>;
  }

  async function loadSnapshot() {
    isLoading = true;
    errorMessage = null;
    try {
      snapshot = await api<AgentSnapshot>('/api/v1/agents/snapshot');
    } catch (error) {
      errorMessage = error instanceof Error ? error.message : 'Snapshot failed';
    } finally {
      isLoading = false;
    }
  }

  async function createRequirement() {
    if (!title.trim()) return;

    isSubmitting = true;
    errorMessage = null;
    try {
      snapshot = await api<AgentSnapshot>('/api/v1/agents/requirements', {
        method: 'POST',
        body: JSON.stringify({ title, description }),
      });
      title = '';
      description = '';
    } catch (error) {
      errorMessage = error instanceof Error ? error.message : 'Requirement failed';
    } finally {
      isSubmitting = false;
    }
  }

  function handleRequirementSubmit(event: SubmitEvent) {
    event.preventDefault();
    createRequirement();
  }

  async function autoClaim() {
    snapshot = await api<AgentSnapshot>('/api/v1/agents/auto-claim', { method: 'POST' });
  }

  async function claimFor(account: AgentAccount) {
    snapshot = await api<AgentSnapshot>(`/api/v1/agents/accounts/${account.accountCode}/claim`, { method: 'POST' });
  }

  async function setTaskStatus(task: AgentTask, status: AgentTaskStatus) {
    snapshot = await api<AgentSnapshot>(`/api/v1/agents/tasks/${task.id}/status`, {
      method: 'POST',
      body: JSON.stringify({ status }),
    });
  }

  function formatTime(value: string | null) {
    if (!value) return 'never';
    return new Intl.DateTimeFormat(undefined, {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    }).format(new Date(value));
  }

  onMount(loadSnapshot);
</script>

<svelte:head>
  <title>Eneik Agent Orchestrator</title>
</svelte:head>

<main class="page">
  <header class="topbar">
    <div>
      <p class="eyebrow">Eneik Production OS</p>
      <h1>Agent Orchestrator</h1>
    </div>
    <div class="topbar-actions">
      <a href="http://localhost:8080/" target="_blank" rel="noreferrer">Backend</a>
      <a href="http://localhost:8000/docs" target="_blank" rel="noreferrer">ML</a>
      <button type="button" onclick={loadSnapshot} disabled={isLoading}>Refresh</button>
    </div>
  </header>

  {#if errorMessage}
    <div class="alert">{errorMessage}</div>
  {/if}

  <section class="summary-band" aria-label="System summary">
    <div>
      <span>Accounts</span>
      <strong>{snapshot.summary.accounts ?? snapshot.accounts.length}</strong>
    </div>
    <div>
      <span>Claimed</span>
      <strong>{snapshot.summary.claimed ?? 0}</strong>
    </div>
    <div>
      <span>Todo</span>
      <strong>{snapshot.summary.todo ?? 0}</strong>
    </div>
    <div>
      <span>Done</span>
      <strong>{snapshot.summary.done ?? 0}</strong>
    </div>
  </section>

  <section class="workspace">
    <form class="intake" onsubmit={handleRequirementSubmit}>
      <div class="section-heading">
        <h2>Requirement Intake</h2>
        <button type="submit" disabled={isSubmitting || !title.trim()}>
          {isSubmitting ? 'Creating' : 'Create and Assign'}
        </button>
      </div>

      <label>
        Title
        <input bind:value={title} placeholder="New operational requirement" />
      </label>

      <label>
        Scope
        <textarea bind:value={description} rows="7" placeholder="Describe the system change"></textarea>
      </label>
    </form>

    <section class="accounts" aria-label="Agent accounts">
      <div class="section-heading">
        <h2>Accounts</h2>
        <button type="button" onclick={autoClaim}>Auto Claim</button>
      </div>

      <div class="account-list">
        {#each snapshot.accounts as account}
          <article class="account-row">
            <div>
              <strong>{account.accountCode}</strong>
              <span>{account.displayName}</span>
            </div>
            <div class="tag-row">
              {#each account.focusTags as tag}
                <span class="tag">{tag}</span>
              {/each}
            </div>
            <div class="account-actions">
              <span class:active={account.status === 'ACTIVE'}>{account.status}</span>
              <button type="button" onclick={() => claimFor(account)}>Claim</button>
            </div>
          </article>
        {/each}
      </div>
    </section>
  </section>

  <section class="tasks">
    <div class="section-heading">
      <h2>Task Queue</h2>
      <select bind:value={statusFilter} aria-label="Task status filter">
        {#each statusOptions as status}
          <option value={status}>{status}</option>
        {/each}
      </select>
    </div>

    <div class="task-list">
      {#each filteredTasks as task}
        <article class="task-row">
          <div class="task-main">
            <div class="task-meta">
              <span class="tag">{task.agentTag}</span>
              <span class:claimed={task.status === 'CLAIMED'} class:done={task.status === 'DONE'}>{task.status}</span>
              <span>{formatTime(task.createdAt)}</span>
            </div>
            <h3>{task.requirementTitle}</h3>
            <p>{task.description}</p>
          </div>
          <div class="task-owner">
            <span>{task.claimedByAccountCode ?? 'Unclaimed'}</span>
            <small>{task.claimedByDisplayName ?? 'Waiting for matching account'}</small>
          </div>
          <div class="task-actions">
            <button type="button" onclick={() => setTaskStatus(task, 'IN_PROGRESS')}>Start</button>
            <button type="button" onclick={() => setTaskStatus(task, 'REVIEW')}>Review</button>
            <button type="button" onclick={() => setTaskStatus(task, 'DONE')}>Done</button>
          </div>
        </article>
      {:else}
        <div class="empty">No tasks in this state.</div>
      {/each}
    </div>
  </section>
</main>

<style>
  .page {
    min-height: 100vh;
    padding: 24px;
    background: #f6f7f9;
  }

  .topbar,
  .summary-band,
  .workspace,
  .tasks {
    width: min(1440px, 100%);
    margin: 0 auto;
  }

  .topbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    padding: 8px 0 20px;
  }

  .eyebrow {
    margin: 0 0 4px;
    color: #687385;
    font-size: 13px;
    font-weight: 700;
    text-transform: uppercase;
  }

  h1,
  h2,
  h3,
  p {
    margin: 0;
  }

  h1 {
    color: #111827;
    font-size: 34px;
    line-height: 1.1;
    letter-spacing: 0;
  }

  h2 {
    color: #18212f;
    font-size: 18px;
    letter-spacing: 0;
  }

  h3 {
    color: #18212f;
    font-size: 16px;
    letter-spacing: 0;
  }

  button,
  a,
  select,
  input,
  textarea {
    border-radius: 6px;
  }

  button,
  a {
    border: 1px solid #c8d0dc;
    background: #ffffff;
    color: #18212f;
    padding: 9px 12px;
    text-decoration: none;
    font-weight: 700;
  }

  button:hover,
  a:hover {
    border-color: #5b7cfa;
  }

  button:disabled {
    cursor: not-allowed;
    opacity: 0.55;
  }

  .topbar-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .alert {
    width: min(1440px, 100%);
    margin: 0 auto 16px;
    padding: 12px 14px;
    border: 1px solid #f0b5b5;
    border-radius: 6px;
    background: #fff1f1;
    color: #a33131;
  }

  .summary-band {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 1px;
    overflow: hidden;
    border: 1px solid #dbe1ea;
    border-radius: 8px;
    background: #dbe1ea;
  }

  .summary-band div {
    display: flex;
    flex-direction: column;
    gap: 6px;
    padding: 18px;
    background: #ffffff;
  }

  .summary-band span {
    color: #687385;
    font-size: 13px;
    font-weight: 700;
    text-transform: uppercase;
  }

  .summary-band strong {
    color: #111827;
    font-size: 28px;
    line-height: 1;
  }

  .workspace {
    display: grid;
    grid-template-columns: minmax(320px, 420px) minmax(0, 1fr);
    gap: 16px;
    margin-top: 16px;
  }

  .intake,
  .accounts,
  .tasks {
    border: 1px solid #dbe1ea;
    border-radius: 8px;
    background: #ffffff;
  }

  .intake,
  .accounts {
    padding: 16px;
  }

  .section-heading {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 14px;
  }

  label {
    display: grid;
    gap: 8px;
    margin-bottom: 12px;
    color: #485468;
    font-size: 13px;
    font-weight: 700;
    text-transform: uppercase;
  }

  input,
  textarea,
  select {
    width: 100%;
    border: 1px solid #c8d0dc;
    background: #ffffff;
    color: #18212f;
    padding: 10px 12px;
  }

  textarea {
    resize: vertical;
  }

  .account-list,
  .task-list {
    display: grid;
    gap: 10px;
  }

  .account-row,
  .task-row {
    border: 1px solid #e2e7ef;
    border-radius: 8px;
    background: #ffffff;
  }

  .account-row {
    display: grid;
    grid-template-columns: minmax(170px, 220px) minmax(0, 1fr) auto;
    align-items: center;
    gap: 12px;
    padding: 12px;
  }

  .account-row strong,
  .account-row span,
  .task-owner span,
  .task-owner small {
    display: block;
  }

  .account-row strong {
    color: #111827;
  }

  .account-row span,
  .task-owner small,
  .task-meta {
    color: #687385;
    font-size: 13px;
  }

  .tag-row,
  .task-meta {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }

  .tag {
    display: inline-flex;
    align-items: center;
    min-height: 24px;
    border: 1px solid #c8d0dc;
    border-radius: 999px;
    padding: 2px 8px;
    background: #f8fafc;
    color: #334155;
    font-size: 12px;
    font-weight: 800;
  }

  .account-actions,
  .task-actions {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 8px;
  }

  .account-actions span {
    border-radius: 999px;
    padding: 4px 8px;
    background: #edf1f6;
    color: #485468;
    font-size: 12px;
    font-weight: 800;
  }

  .account-actions span.active {
    background: #e9f8ef;
    color: #1f7a3d;
  }

  .tasks {
    margin-top: 16px;
    padding: 16px;
  }

  .task-row {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(150px, 220px) auto;
    gap: 14px;
    align-items: center;
    padding: 14px;
  }

  .task-main {
    display: grid;
    gap: 8px;
  }

  .task-main p {
    color: #485468;
  }

  .task-owner {
    display: grid;
    gap: 4px;
  }

  .task-owner span {
    color: #18212f;
    font-weight: 800;
  }

  .task-meta span:not(.tag) {
    border-radius: 999px;
    padding: 3px 8px;
    background: #edf1f6;
    font-weight: 800;
  }

  .task-meta span.claimed {
    background: #fff5d7;
    color: #926700;
  }

  .task-meta span.done {
    background: #e9f8ef;
    color: #1f7a3d;
  }

  .empty {
    padding: 24px;
    border: 1px dashed #c8d0dc;
    border-radius: 8px;
    color: #687385;
    text-align: center;
  }

  @media (max-width: 980px) {
    .topbar,
    .workspace,
    .task-row,
    .account-row {
      grid-template-columns: 1fr;
    }

    .topbar {
      display: grid;
    }

    .summary-band {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    .account-actions,
    .task-actions {
      justify-content: flex-start;
      flex-wrap: wrap;
    }
  }
</style>
