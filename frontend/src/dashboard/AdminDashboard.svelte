<script lang="ts">
  import { onMount } from 'svelte';

  const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

  type Setting = {
    key: string;
    enabled: boolean | null;
    maskedValue: string | null;
    source: 'database' | 'env' | 'none';
  };

  type Section<T> = {
    available: boolean;
    data: T;
    error?: string;
  };

  type AccountItem = {
    id: string;
    name: string;
    status: 'idle' | 'busy' | 'offline' | 'decommissioned';
    currentProjectId?: string;
    capabilities: string;
    lastHeartbeat?: string;
    apiKeyMasked?: string;
    githubUsername?: string;
    enabled: boolean;
  };

  type Collaborator = {
    username: string;
    status: string;
    githubStatus: string;
    detail: string;
    statusLabel: string;
    uiColorToken: string;
  };

  type ProjectDto = {
    id: string;
    name: string;
    statusLabel: string;
    uiColorToken: string;
    repositoryName: string;
    factoryReport?: string;
    collaborators: Collaborator[];
  };

  type AccountsData = {
    total: number;
    idle: number;
    busy: number;
    offline: number;
    items: AccountItem[];
  };

  type ActiveConflict = {
    id: string;
    taskId: string;
    taskDescription: string;
    prUrl: string;
    detectedAt: string;
    conflictType: string;
    resolutionAttempts: number;
    resolutionStatus: string;
    conflictingFiles?: string;
  };

  type ConflictDpmoData = {
    dpmo: number;
    dpmoLast7Days: number;
    totalMergeAttempts: number;
    conflicts: number;
    activeConflicts: ActiveConflict[];
  };

  type SystemStatus = {
    integrations: Section<Setting[]>;
    accounts: Section<AccountsData>;
    githubAccess: Section<{ latest: Record<string, unknown> | null }>;
    linearCompleteness: Section<Record<string, unknown>>;
    julesSessions: Section<Record<string, number>>;
    qualityGate: Section<Record<string, number>>;
    tasks: Section<Record<string, number>>;
    conflictDpmo?: Section<ConflictDpmoData>;
  };

  const integrations = [
    { name: 'GitHub', enabledKey: 'github_enabled', secretKey: 'github_token' },
    { name: 'Linear', enabledKey: 'linear_enabled', secretKey: 'linear_api_key', extraKey: 'linear_team_id' },
    { name: 'Jules', enabledKey: 'jules_enabled', secretKey: 'jules_api_key' }
  ];

  let status = $state<SystemStatus | null>(null);
  let activeProject = $state<ProjectDto | null>(null);
  let settings = $state<Setting[]>([]);
  let drafts = $state<Record<string, string>>({});
  let editing = $state<Record<string, boolean>>({});
  let newName = $state('');
  let newCapabilities = $state('BARCAN-TAG-00,BARCAN-TAG-01,BARCAN-TAG-02,BARCAN-TAG-03,BARCAN-TAG-04,BARCAN-TAG-05,BARCAN-TAG-06,BARCAN-TAG-07,BARCAN-TAG-08,BARCAN-TAG-09,BARCAN-TAG-10,BARCAN-TAG-11');
  let newGithubUsername = $state('');
  let newApiKey = $state('');
  let message = $state('Ready');

  const settingByKey = (key: string) => settings.find((setting) => setting.key === key);


  function upsertSetting(saved: Setting) {
    settings = settings.some((setting) => setting.key === saved.key)
      ? settings.map((setting) => setting.key === saved.key ? saved : setting)
      : [...settings, saved];
    if (status?.integrations) {
      status = {
        ...status,
        integrations: {
          ...status.integrations,
          data: settings
        }
      };
    }
  }

  async function loadStatus() {
    const response = await fetch(`${API_BASE}/api/system-status`);
    if (!response.ok) {
      message = `System status failed: ${response.status}`;
      return;
    }
    status = await response.json();
    settings = status?.integrations?.data || [];
    await loadActiveProject();
    message = 'Updated';
  }

  async function loadActiveProject() {
    const response = await fetch(`${API_BASE}/api/projects`);
    if (response.ok) {
      const projects = await response.json();
      activeProject = projects.find((p: any) => p.status === 'active') || null;
    }
  }

  async function refreshCollaborators() {
    if (!activeProject) return;
    message = 'Refreshing collaborators...';
    const response = await fetch(`${API_BASE}/api/projects/${activeProject.id}/collaborators/refresh`, {
      method: 'POST'
    });
    if (response.ok) {
      activeProject = await response.json();
      message = 'Collaborators refreshed';
    } else {
      message = 'Refresh failed';
    }
  }

  async function updateAccount(account: AccountItem, updates: any) {
    const response = await fetch(`${API_BASE}/api/accounts/${account.id}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(updates)
    });
    if (response.ok) {
      editing[`acc_${account.id}`] = false;
      drafts[`acc_${account.id}`] = '';
      await loadStatus();
      message = 'Account updated';
    }
  }

  async function createAccount() {
    if (!newName.trim()) return;
    const response = await fetch(`${API_BASE}/api/accounts`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: newName,
        capabilities: newCapabilities,
        githubUsername: newGithubUsername,
        apiKey: newApiKey
      })
    });
    if (response.ok) {
      newName = '';
      newGithubUsername = '';
      newApiKey = '';
      await loadStatus();
      message = 'Account created';
    } else {
        const err = await response.json();
        message = `Create failed: ${err.error || response.status}`;
    }
  }

  async function saveSetting(key: string, value: string) {
    const response = await fetch(`${API_BASE}/api/settings`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ key, value })
    });
    if (!response.ok) {
      message = `Save failed: ${key}`;
      return;
    }
    upsertSetting(await response.json());
    drafts = { ...drafts, [key]: '' };
    editing = { ...editing, [key]: false };
    await loadStatus();
    message = `${key} saved`;
  }

  function startEdit(key: string) {
    editing = { ...editing, [key]: true };
    drafts = { ...drafts, [key]: '' };
  }

  function formatDate(value?: string) {
    if (!value) return 'never';
    return new Date(value).toLocaleString();
  }

  onMount(loadStatus);
</script>

<section class="admin-shell">
  <div class="admin-header">
    <div>
      <p class="eyebrow">Local Administrator</p>
      <h2>Technical System Dashboard</h2>
    </div>
    <button type="button" onclick={loadStatus}>Refresh</button>
  </div>

  <section class="admin-grid overview">
    <div class="stat primary">
      <span class="text-lg {activeProject?.uiColorToken}">{activeProject?.name || 'No Active Project'}</span>
      <p>Active Project ({activeProject?.statusLabel || 'NONE'})</p>
    </div>
    <div class="stat">
      <span>{status?.tasks?.data?.queued ?? 0}</span>
      <p>Queued tasks</p>
    </div>
    <div class="stat">
      <span>{status?.julesSessions?.data?.running ?? 0}</span>
      <p>Jules running</p>
    </div>
    <div class="stat">
      <span>{Math.round(status?.qualityGate?.data?.dpmo ?? 0)}</span>
      <p>Gate DPMO</p>
    </div>
    <div class="stat">
      <span>{Math.round(status?.conflictDpmo?.data?.dpmo ?? 0)}</span>
      <p>Conflict DPMO</p>
    </div>
  </section>

  <section class="admin-panel">
    <div class="panel-head">
      <h2>Интеграции</h2>
      <span>{status?.integrations?.available ? 'online' : 'degraded'}</span>
    </div>
    <div class="integration-grid">
      {#each integrations.filter(i => i.name !== 'Jules') as integration}
        <article class="integration-row">
          <div class="integration-title">
            <h3>{integration.name}</h3>
            <label class="toggle">
              <input
                type="checkbox"
                checked={settingByKey(integration.enabledKey)?.enabled === true}
                onchange={(event) => saveSetting(integration.enabledKey, String((event.currentTarget as HTMLInputElement).checked))}
              />
              <span>enabled</span>
            </label>
          </div>
            <div class="setting-line">
              <span>token</span>
              {#if editing[integration.secretKey]}
                <input bind:value={drafts[integration.secretKey]} placeholder="new token" />
              {:else}
                <input value={settingByKey(integration.secretKey)?.maskedValue || ''} placeholder="not set" disabled />
              {/if}
              <button type="button" class="secondary" onclick={() => startEdit(integration.secretKey)}>Изменить</button>
              <button type="button" onclick={() => saveSetting(integration.secretKey, drafts[integration.secretKey] || '')} disabled={!editing[integration.secretKey]}>
                Сохранить
              </button>
            </div>

          {#if integration.extraKey}
            <div class="setting-line">
              <span>team</span>
              {#if editing[integration.extraKey]}
                <input bind:value={drafts[integration.extraKey]} placeholder="Linear team id" />
              {:else}
                <input value={settingByKey(integration.extraKey)?.maskedValue || ''} placeholder="not set" disabled />
              {/if}
              <button type="button" class="secondary" onclick={() => startEdit(integration.extraKey)}>Изменить</button>
              <button type="button" onclick={() => saveSetting(integration.extraKey, drafts[integration.extraKey] || '')} disabled={!editing[integration.extraKey]}>
                Сохранить
              </button>
            </div>
          {/if}

          <div class="source-line">
            <small>{settingByKey(integration.secretKey)?.source || 'none'}</small>
            {#if integration.name === 'GitHub'}
              <small>{status?.githubAccess?.data?.latest?.ci_status || 'no check yet'}</small>
            {:else if integration.name === 'Linear'}
              <small>{status?.linearCompleteness?.data?.totalIssues ?? 0} issues</small>
            {:else}
              <small>{status?.julesSessions?.data?.total ?? 0} sessions</small>
            {/if}
          </div>
        </article>
      {/each}
    </div>
  </section>

  <section class="admin-panel">
    <div class="panel-head">
      <h2>Account Pool (Jules)</h2>
      <span>{status?.accounts?.data?.items?.filter(a => a.status !== 'decommissioned').length ?? 0} active pool</span>
    </div>

    <div class="create-account-form" style="margin-bottom: 20px; padding: 15px; background: #f1f5f9; border-radius: 6px; display: flex; flex-direction: column; gap: 10px;">
      <div style="display: flex; gap: 10px; align-items: flex-end;">
        <div style="flex: 1;">
          <p class="label">New Account Name</p>
          <input bind:value={newName} placeholder="e.g. Jules-08" style="padding: 8px; width: 100%;" />
        </div>
        <div style="flex: 1;">
          <p class="label">GitHub Username</p>
          <input bind:value={newGithubUsername} placeholder="e.g. jules-bot" style="padding: 8px; width: 100%;" />
        </div>
        <div style="flex: 1;">
          <p class="label">Jules API Key</p>
          <input bind:value={newApiKey} type="password" placeholder="sk-..." style="padding: 8px; width: 100%;" />
        </div>
      </div>
      <div style="display: flex; gap: 10px; align-items: flex-end;">
        <div style="flex: 1;">
          <p class="label">Capabilities (comma separated)</p>
          <input bind:value={newCapabilities} placeholder="BARCAN-TAG-01,..." style="padding: 8px; width: 100%;" />
        </div>
        <button onclick={createAccount} style="height: 38px; padding: 0 20px;">Add Account</button>
      </div>
    </div>

    <div class="account-summary">
      <span class="idle">{status?.accounts?.data?.items?.filter(a => a.status === 'idle').length ?? 0} idle</span>
      <span class="busy">{status?.accounts?.data?.items?.filter(a => a.status === 'busy').length ?? 0} busy</span>
      <span class="offline">{status?.accounts?.data?.items?.filter(a => a.status === 'offline').length ?? 0} offline</span>
    </div>
    <div class="account-table">
      <div class="table-head">
        <span>Account Name</span>
        <span>Status</span>
        <span>API Key (Jules)</span>
        <span>Last Activity</span>
        <span>Project Context</span>
        <span>Actions</span>
      </div>
      {#each status?.accounts?.data?.items || [] as account}
        {#if account.status !== 'decommissioned'}
          <div class="table-row">
            <div class="flex flex-col">
              <strong>{account.name}</strong>
              <small>{account.capabilities}</small>
            </div>
            <span class={`pill ${account.status}`}>{account.status}</span>
            <div class="flex items-center gap-2">
              {#if editing[`acc_${account.id}`]}
                <input bind:value={drafts[`acc_${account.id}`]} placeholder="new api key" class="text-xs" />
                <button type="button" class="mini-btn" onclick={() => updateAccount(account, { apiKey: drafts[`acc_${account.id}`] })}>Save</button>
              {:else}
                <span class="text-xs font-mono">{account.apiKeyMasked || '••••••••'}</span>
                <button type="button" class="mini-btn secondary" onclick={() => {
                  editing[`acc_${account.id}`] = true;
                  drafts[`acc_${account.id}`] = '';
                }}>Edit</button>
              {/if}
            </div>
            <span>{formatDate(account.lastHeartbeat)}</span>
            <span class="text-xs">{account.currentProjectId || 'Global Pool'}</span>
            <div class="flex gap-2">
               <label class="toggle mini">
                  <input type="checkbox" checked={account.enabled} onchange={(e) => updateAccount(account, { enabled: e.currentTarget.checked })} />
                  <span>Enabled</span>
               </label>
               <button type="button" class="mini-btn danger" onclick={() => {
                 if(confirm('Decommission this account?')) updateAccount(account, { status: 'decommissioned' })
               }}>Decommission</button>
            </div>
          </div>
        {/if}
      {/each}
    </div>
  </section>

  <section class="admin-panel">
    <div class="panel-head">
      <h2>GitHub access for Jules</h2>
      <div class="flex items-center gap-2">
        <span>{activeProject?.repositoryName || 'no active repository'}</span>
        {#if activeProject}
          <button onclick={refreshCollaborators} class="mini-btn">Refresh</button>
        {/if}
      </div>
    </div>
    {#if activeProject?.collaborators?.length}
      <div class="collaborator-list">
        {#each activeProject.collaborators as collaborator}
          <div class="collaborator-row">
            <strong>{collaborator.username}</strong>
            <span class={`pill ${collaborator.uiColorToken}`}>
              {collaborator.statusLabel}
            </span>
            <small>GitHub HTTP {collaborator.githubStatus}</small>
            <small>{collaborator.detail}</small>
          </div>
        {/each}
      </div>
    {:else if activeProject}
      <p class="warning-text">
        No collaborator invitation result is recorded for this project. Create a new project after the latest Project Factory fix.
      </p>
    {:else}
      <p class="warning-text">No active project selected.</p>
    {/if}
  </section>

  <section class="admin-panel">
    <div class="panel-head">
      <h2>Conflict DPMO & Merge Failures</h2>
      <span>{status?.conflictDpmo?.data?.conflicts ?? 0} total defects</span>
    </div>
    
    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 15px; margin-bottom: 20px;">
      <div style="background: #f8fafc; padding: 15px; border-radius: 6px; border: 1px solid #e2e8f0;">
        <h4 style="margin: 0 0 5px 0; color: #64748b; font-size: 13px; text-transform: uppercase;">DPMO (All Time)</h4>
        <span style="font-size: 24px; font-weight: 800; color: #b91c1c;">{Math.round(status?.conflictDpmo?.data?.dpmo ?? 0)}</span>
        <p style="margin: 5px 0 0 0; font-size: 12px; color: #64748b;">
          {status?.conflictDpmo?.data?.conflicts ?? 0} conflicts / {status?.conflictDpmo?.data?.totalMergeAttempts ?? 0} merge attempts
        </p>
      </div>
      <div style="background: #f8fafc; padding: 15px; border-radius: 6px; border: 1px solid #e2e8f0;">
        <h4 style="margin: 0 0 5px 0; color: #64748b; font-size: 13px; text-transform: uppercase;">DPMO (Last 7 Days)</h4>
        <span style="font-size: 24px; font-weight: 800; color: #b91c1c;">{Math.round(status?.conflictDpmo?.data?.dpmoLast7Days ?? 0)}</span>
        <p style="margin: 5px 0 0 0; font-size: 12px; color: #64748b;">
          Trend: {Math.round(status?.conflictDpmo?.data?.dpmoLast7Days ?? 0) < Math.round(status?.conflictDpmo?.data?.dpmo ?? 0) ? '▼ Decreasing' : '▲ Increasing/Stable'}
        </p>
      </div>
    </div>

    <h3>Active Merge Conflicts</h3>
    {#if status?.conflictDpmo?.data?.activeConflicts?.length}
      <div style="display: grid; gap: 10px; margin-top: 10px;">
        {#each status.conflictDpmo.data.activeConflicts as conflict}
          <div style="background: #fffbeb; border: 1px solid #fef3c7; border-radius: 6px; padding: 12px; display: flex; flex-direction: column; gap: 5px;">
            <div style="display: flex; justify-content: space-between; align-items: center;">
              <strong style="color: #92400e;">Task ID: {conflict.taskId}</strong>
              <span class={`pill ${conflict.resolutionStatus === 'escalated' ? 'offline' : 'busy'}`}>{conflict.resolutionStatus}</span>
            </div>
            <p style="margin: 0; font-size: 13px; color: #4b5563;">{conflict.taskDescription}</p>
            {#if conflict.prUrl}
              <a href={conflict.prUrl} target="_blank" style="font-size: 12px; color: #2563eb; text-decoration: underline;">
                PR Link: {conflict.prUrl}
              </a>
            {/if}
            <div style="display: flex; gap: 15px; font-size: 11px; color: #6b7280; margin-top: 5px;">
              <span>Detected: {formatDate(conflict.detectedAt)}</span>
              <span>Attempts: {conflict.resolutionAttempts}/3</span>
              {#if conflict.conflictingFiles}
                <span>Conflicting Files: {conflict.conflictingFiles}</span>
              {/if}
            </div>
          </div>
        {/each}
      </div>
    {:else}
      <p style="color: #6b7280; font-size: 13px; margin: 10px 0 0 0;">No active merge conflicts detected.</p>
    {/if}
  </section>

  <section class="admin-panel compact">
    <div>
      <h2>Общая картина</h2>
      <p>GitHub: {status?.githubAccess?.available ? (status?.githubAccess?.data?.latest?.ci_status || 'no check') : 'unavailable'}</p>
      <p>Linear completeness: {Math.round(((status?.linearCompleteness?.data?.completeness_rate as number) || 0) * 100)}%</p>
      <p>Jules stuck: {status?.julesSessions?.data?.stuck ?? 0}</p>
      <p>Quality defects: {status?.qualityGate?.data?.defects ?? 0}</p>
    </div>
    <p class="admin-message">{message}</p>
  </section>
</section>

<style>
  .admin-shell {
    display: grid;
    gap: 18px;
  }

  .admin-header,
  .panel-head,
  .integration-title,
  .setting-line,
  .source-line,
  .account-summary {
    align-items: center;
    display: flex;
    gap: 12px;
    justify-content: space-between;
  }

  .admin-grid {
    display: grid;
    gap: 12px;
    grid-template-columns: repeat(5, minmax(140px, 1fr));
  }

  .admin-panel,
  .stat {
    background: white;
    border: 1px solid #dbe3ef;
    border-radius: 8px;
    padding: 18px;
  }

  .stat span {
    display: block;
    font-size: 28px;
    font-weight: 800;
  }

  .stat.primary {
    border-color: #1d4ed8;
    background: #eff6ff;
  }

  .text-success { color: #0F766E; }
  .text-warning { color: #B45309; }
  .text-primary { color: #1D4ED8; }
  .text-secondary { color: #64748B; }
  .text-neutral-500 { color: #64748B; }

  .text-lg { font-size: 1.125rem; }
  .text-xs { font-size: 0.75rem; }
  .font-mono { font-family: monospace; }
  .flex { display: flex; }
  .flex-col { flex-direction: column; }
  .items-center { align-items: center; }
  .gap-2 { gap: 0.5rem; }

  .mini-btn {
    padding: 2px 6px;
    font-size: 10px;
    border-radius: 4px;
    cursor: pointer;
  }

  .mini-btn.secondary { background: #64748b; color: white; }
  .mini-btn.danger { background: #ef4444; color: white; }

  .integration-grid {
    display: grid;
    gap: 12px;
    grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
    margin-top: 14px;
  }

  .integration-row {
    background: #f8fafc;
    display: grid;
    gap: 12px;
  }

  .integration-row h3 {
    font-size: 18px;
    margin: 0;
  }

  .toggle {
    align-items: center;
    display: flex;
    gap: 8px;
  }

  .toggle input {
    width: auto;
  }

  .setting-line {
    display: grid;
    grid-template-columns: 44px minmax(0, 1fr) auto auto;
  }

  .token-name-label {
    font-weight: 600;
    font-size: 0.8rem;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .add-token {
    grid-template-columns: 1fr 1.5fr auto !important;
    margin-top: 12px;
    border-top: 1px solid #e2e8f0;
    padding-top: 12px;
  }

  .secondary {
    background: #475569;
  }

  .danger {
    background: #ef4444;
  }

  .mini {
    transform: scale(0.8);
  }

  .source-line small {
    color: #64748b;
  }

  .account-summary {
    justify-content: flex-start;
    margin: 12px 0;
  }

  .account-summary span,
  .pill {
    border-radius: 999px;
    display: inline-flex;
    font-size: 12px;
    font-weight: 800;
    padding: 4px 9px;
    text-transform: uppercase;
  }

  .idle {
    background: #dcfce7;
    color: #047857;
  }

  .busy {
    background: #fef3c7;
    color: #b45309;
  }

  .offline {
    background: #fee2e2;
    color: #b91c1c;
  }

  .account-table {
    display: grid;
    gap: 8px;
    overflow-x: auto;
  }

  .collaborator-list {
    display: grid;
    gap: 10px;
    margin-top: 12px;
  }

  .collaborator-row {
    align-items: center;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 6px;
    display: grid;
    gap: 10px;
    grid-template-columns: 180px 190px 120px minmax(0, 1fr);
    padding: 12px;
  }

  .warning-text {
    color: #92400e;
    margin: 0;
  }

  .table-head,
  .table-row {
    display: grid;
    gap: 12px;
    grid-template-columns: minmax(160px, 1fr) 90px 180px minmax(150px, 1fr) minmax(150px, 1fr) minmax(160px, 1fr);
    min-width: 1000px;
  }

  .table-head {
    color: #64748b;
    font-size: 12px;
    font-weight: 800;
    text-transform: uppercase;
  }

  .table-row {
    align-items: center;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 6px;
    padding: 10px;
  }

  .compact {
    align-items: end;
    display: flex;
    justify-content: space-between;
  }

  .admin-message {
    color: #475569;
  }

  @media (max-width: 900px) {
    .admin-grid,
    .integration-grid {
      grid-template-columns: 1fr;
    }

    .setting-line,
    .compact {
      align-items: stretch;
      display: grid;
      grid-template-columns: 1fr;
    }
  }
</style>
