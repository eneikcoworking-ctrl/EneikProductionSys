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
    operational?: number;
    apiKeyConfigured?: number;
    idle: number;
    busy: number;
    offline: number;
    decommissioned?: number;
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

  type IntegrationConfig = {
    name: string;
    enabledKey: string;
    secretKey?: string;
    extraKey?: string;
    pushKey?: string;
  };

  const integrations: IntegrationConfig[] = [
    { name: 'GitHub', enabledKey: 'github_enabled', secretKey: 'github_token' },
    { name: 'Linear', enabledKey: 'linear_enabled', secretKey: 'linear_api_key', extraKey: 'linear_team_id' },
    { name: 'Jules', enabledKey: 'jules_enabled', secretKey: 'jules_api_key' },
    { name: 'Gemini', enabledKey: 'gemini_enabled', secretKey: 'gemini_api_key' },
    { name: 'Claude Worker', enabledKey: 'claude_worker_enabled', secretKey: 'anthropic_api_key', extraKey: 'claude_worker_model', pushKey: 'claude_worker_push_enabled' }
  ];

  let status = $state<SystemStatus | null>(null);
  let activeProject = $state<ProjectDto | null>(null);
  let settings = $state<Setting[]>([]);
  let drafts = $state<Record<string, string>>({});
  let editing = $state<Record<string, boolean>>({});
  let newName = $state('');
  const allBarcanCapabilities = 'BARCAN-TAG-00,BARCAN-TAG-01,BARCAN-TAG-02,BARCAN-TAG-03,BARCAN-TAG-04,BARCAN-TAG-05,BARCAN-TAG-06,BARCAN-TAG-07,BARCAN-TAG-08,BARCAN-TAG-09,BARCAN-TAG-10,BARCAN-TAG-11';
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
    try {
      const response = await fetch(`${API_BASE}/api/system-status`);
      if (!response.ok) {
        message = `System status failed: ${response.status}`;
        return;
      }
      status = await response.json();
      settings = status?.integrations?.data || [];
      await loadActiveProject();
      message = 'Updated';
    } catch (e) {
      message = `Network error while loading system status: ${e}`;
    }
  }

  async function loadActiveProject() {
    try {
      const response = await fetch(`${API_BASE}/api/projects`);
      if (response.ok) {
        const projects = await response.json();
        activeProject = projects.find((p: any) => p.status === 'active') || null;
      }
    } catch (e) {
      message = `Network error while loading active project: ${e}`;
    }
  }

  async function refreshCollaborators() {
    if (!activeProject) return;
    message = 'Refreshing collaborators...';
    try {
      const response = await fetch(`${API_BASE}/api/projects/${activeProject.id}/collaborators/refresh`, {
        method: 'POST'
      });
      if (response.ok) {
        activeProject = await response.json();
        message = 'Collaborators refreshed';
      } else {
        message = 'Refresh failed';
      }
    } catch (e) {
      message = `Network error while refreshing collaborators: ${e}`;
    }
  }

  async function updateAccount(account: AccountItem, updates: any) {
    try {
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
      } else {
        message = `Account update failed: ${response.status}`;
      }
    } catch (e) {
      message = `Network error while updating account: ${e}`;
    }
  }

  async function createAccount() {
    if (!newName.trim()) return;
    const response = await fetch(`${API_BASE}/api/accounts`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: newName,
        capabilities: allBarcanCapabilities,
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
    try {
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
    } catch (e) {
      message = `Network error while saving ${key}: ${e}`;
    }
  }

  function startEdit(key: string, isSecret = true) {
    editing = { ...editing, [key]: true };
    // Secrets can't be pre-filled (only a masked value is ever returned), so those start blank. Non-secret
    // fields (e.g. linear_team_id, claude_worker_model) start from the current value — otherwise clicking
    // Edit then Save without typing anything silently overwrites a working value with an empty string.
    const current = isSecret ? '' : (settingByKey(key)?.maskedValue || '');
    drafts = { ...drafts, [key]: current };
  }

  function formatDate(value?: string) {
    if (!value) return 'never';
    return new Date(value).toLocaleString();
  }

  function capabilityList(value?: string) {
    return (value || '')
      .split(',')
      .map((capability) => capability.trim())
      .filter(Boolean);
  }

  function capabilitySummary(value?: string) {
    const capabilities = capabilityList(value);
    if (capabilities.length === 0) return 'No capabilities';
    if (capabilities.length >= 12) return 'All 12 BARCAN roles';
    if (capabilities.length === 1) return capabilities[0];
    return `${capabilities.length} roles`;
  }

  function collaboratorRank(collaborator: Collaborator) {
    const label = `${collaborator.status || ''} ${collaborator.statusLabel || ''}`.toLowerCase();
    if (label.includes('collaborator')) return 4;
    if (label.includes('sent')) return 3;
    if (label.includes('pending')) return 2;
    if (label.includes('failed') || label.includes('error')) return 1;
    return 0;
  }

  function uniqueCollaborators(collaborators: Collaborator[] = []) {
    const byUsername = new Map<string, Collaborator>();
    for (const collaborator of collaborators) {
      const key = collaborator.username.toLowerCase();
      const existing = byUsername.get(key);
      if (!existing || collaboratorRank(collaborator) > collaboratorRank(existing)) {
        byUsername.set(key, collaborator);
      }
    }
    return Array.from(byUsername.values()).sort((a, b) => a.username.localeCompare(b.username));
  }

  let chatOpen = $state(false);
  let chatInput = $state('');
  let chatHistory = $state<{ sender: 'user' | 'ai'; text: string }[]>([]);
  let chatLoading = $state(false);

  async function sendChatMessage() {
    if (!chatInput.trim() || chatLoading) return;
    const userMsg = chatInput.trim();
    chatHistory = [...chatHistory, { sender: 'user', text: userMsg }];
    chatInput = '';
    chatLoading = true;

    try {
      const response = await fetch(`${API_BASE}/api/dashboard/chat`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          message: userMsg,
          projectId: activeProject?.id || '',
          projectName: activeProject?.name || '',
          mode: 'operator'
        })
      });
      if (response.ok) {
        const data = await response.json();
        chatHistory = [...chatHistory, { sender: 'ai', text: data.response || 'Empty assistant response.' }];
      } else {
        chatHistory = [...chatHistory, { sender: 'ai', text: 'Error: assistant server is not reachable.' }];
      }
    } catch (e) {
      chatHistory = [...chatHistory, { sender: 'ai', text: `Network error: ${e}` }];
    } finally {
      chatLoading = false;
    }
  }

  onMount(loadStatus);
</script>

<section class="admin-shell">
  <div class="admin-header">
    <div>
      <p class="eyebrow">Local Administrator</p>
      <h2>Technical System Dashboard</h2>
    </div>
    <div class="admin-header-actions">
      <button type="button" onclick={loadStatus}>Refresh</button>
      {#if !chatOpen}
        <button type="button" class="chat-trigger inline" onclick={() => chatOpen = true}>
          Ask AI
        </button>
      {/if}
    </div>
  </div>

  <section class="admin-panel">
    <div class="panel-head">
      <h2>Integrations</h2>
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
          {#if integration.secretKey}
            <div class="setting-line">
              <span>token</span>
              {#if editing[integration.secretKey!]}
                <input bind:value={drafts[integration.secretKey!]} placeholder="new token" />
              {:else}
                <input value={settingByKey(integration.secretKey!)?.maskedValue || ''} placeholder="not set" disabled />
              {/if}
              <button type="button" class="secondary" onclick={() => startEdit(integration.secretKey!)}>Edit</button>
                <button type="button" onclick={() => saveSetting(integration.secretKey!, drafts[integration.secretKey!] || '')} disabled={!editing[integration.secretKey!]}>
                  Save
                </button>
              </div>
          {/if}

          {#if integration.extraKey}
            <div class="setting-line">
              <span>{integration.name === 'Claude Worker' ? 'model' : 'team'}</span>
              {#if editing[integration.extraKey!]}
                <input bind:value={drafts[integration.extraKey!]} placeholder={integration.name === 'Claude Worker' ? 'claude-opus-4-8' : 'Linear team id'} />
              {:else}
                <input value={settingByKey(integration.extraKey!)?.maskedValue || ''} placeholder="not set" disabled />
              {/if}
              <button type="button" class="secondary" onclick={() => startEdit(integration.extraKey!, false)}>Edit</button>
              <button type="button" onclick={() => saveSetting(integration.extraKey!, drafts[integration.extraKey!] || '')} disabled={!editing[integration.extraKey!]}>
                Save
              </button>
            </div>
          {/if}

          {#if integration.pushKey}
            <div class="setting-line">
              <span>branch push</span>
              <label class="toggle inline-toggle">
                <input
                  type="checkbox"
                  checked={settingByKey(integration.pushKey!)?.enabled === true}
                  onchange={(event) => saveSetting(integration.pushKey!, String((event.currentTarget as HTMLInputElement).checked))}
                />
                <span>autonomous branch + PR flow</span>
              </label>
            </div>
          {/if}

          <div class="source-line">
            <small>{integration.secretKey ? settingByKey(integration.secretKey!)?.source || 'none' : 'uses gemini key'}</small>
            {#if integration.name === 'GitHub'}
              <small>{status?.githubAccess?.data?.latest?.ci_status || 'no check yet'}</small>
            {:else if integration.name === 'Linear'}
              <small>{status?.linearCompleteness?.data?.totalIssues ?? 0} issues</small>
            {:else if integration.name === 'Jules'}
              <small>{status?.julesSessions?.data?.total ?? 0} sessions</small>
            {:else if integration.name === 'Claude Worker'}
              <small>{settingByKey(integration.pushKey || '')?.enabled ? 'autonomous branch push enabled' : 'read-only diagnostic'}</small>
            {:else}
              <small>used for AI generation (design/video assets)</small>
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

    <div class="create-account-form">
      <div class="form-field">
        <p class="label">New Account Name</p>
        <input bind:value={newName} placeholder="e.g. Jules-08" />
      </div>
      <div class="form-field">
        <p class="label">GitHub Username</p>
        <input bind:value={newGithubUsername} placeholder="e.g. jules-bot" />
      </div>
      <div class="form-field">
        <p class="label">Jules API Key</p>
        <input bind:value={newApiKey} type="password" placeholder="sk-..." />
      </div>
      <div class="form-field capabilities-field">
        <p class="label">Capabilities</p>
        <input value="All 12 BARCAN roles" title={allBarcanCapabilities} disabled />
      </div>
      <button class="add-account-btn" onclick={createAccount}>Add Account</button>
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
            <div class="account-name-cell">
              <strong>{account.name}</strong>
              <small title={account.capabilities}>{capabilitySummary(account.capabilities)}</small>
            </div>
            <span class={`pill ${account.status}`}>{account.status}</span>
            <div class="key-cell">
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
            <div class="actions-cell">
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
        {#each uniqueCollaborators(activeProject.collaborators) as collaborator}
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

  <section class="admin-panel compact">
    <h2>Pool Status</h2>
    <p class="admin-message">Admin status: {message}</p>
  </section>

  <!-- AI Assistant Chat Widget -->
  {#if chatOpen}
    <div class="chat-widget">
      <div class="chat-box">
        <div class="chat-header">
          <h3>Project Operator Eneik</h3>
          <button type="button" class="close-btn" onclick={() => chatOpen = false}>×</button>
        </div>
        <div class="chat-messages">
          {#each chatHistory as messageItem}
            <div class={`chat-message ${messageItem.sender}`}>
              <div class="message-bubble">{messageItem.text}</div>
            </div>
          {/each}
          {#if chatLoading}
            <div class="chat-message ai">
              <div class="message-bubble loading">Thinking...</div>
            </div>
          {/if}
        </div>
        <div class="chat-input-area">
          <input
            type="text"
            placeholder="Ask about the current project..."
            bind:value={chatInput}
            onkeydown={(e) => { if (e.key === 'Enter') sendChatMessage(); }}
          />
          <button type="button" onclick={sendChatMessage} disabled={chatLoading}>Send</button>
        </div>
      </div>
    </div>
  {/if}
</section>

<style>
  .admin-shell {
    display: grid;
    gap: 18px;
    padding-bottom: 96px;
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

  .admin-header-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    justify-content: flex-end;
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
    grid-template-columns: 96px minmax(0, 1fr) auto auto;
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

  .create-account-form {
    align-items: end;
    background: #f1f5f9;
    border-radius: 8px;
    display: grid;
    gap: 12px;
    grid-template-columns: repeat(3, minmax(150px, 1fr)) minmax(160px, 0.8fr) auto;
    margin-bottom: 20px;
    padding: 15px;
  }

  .form-field {
    min-width: 0;
  }

  .form-field input {
    min-height: 42px;
  }

  .capabilities-field input {
    color: #475569;
    font-weight: 700;
  }

  .add-account-btn {
    height: 42px;
    white-space: nowrap;
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
    padding-bottom: 4px;
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

  .collaborator-row strong,
  .collaborator-row small {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .warning-text {
    color: #92400e;
    margin: 0;
  }

  .table-head,
  .table-row {
    display: grid;
    gap: 12px;
    grid-template-columns: minmax(180px, 1fr) 92px minmax(190px, 0.8fr) minmax(150px, 0.85fr) minmax(140px, 0.8fr) minmax(210px, 1fr);
    min-width: 1120px;
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

  .account-name-cell,
  .key-cell,
  .actions-cell {
    align-items: center;
    display: flex;
    gap: 8px;
    min-width: 0;
  }

  .account-name-cell {
    align-items: flex-start;
    flex-direction: column;
    gap: 2px;
  }

  .account-name-cell small {
    color: #64748b;
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .key-cell input {
    min-width: 0;
  }

  .actions-cell {
    justify-content: flex-start;
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

    .admin-shell {
      padding-bottom: 84px;
    }

    .admin-header,
    .panel-head,
    .integration-title,
    .source-line,
    .account-summary {
      align-items: flex-start;
      flex-direction: column;
    }

    .create-account-form {
      grid-template-columns: 1fr;
    }

    .account-table {
      overflow-x: visible;
    }

    .table-head {
      display: none;
    }

    .table-row {
      grid-template-columns: 1fr;
      min-width: 0;
    }

    .key-cell,
    .actions-cell {
      align-items: flex-start;
      flex-direction: column;
    }

    .collaborator-row {
      grid-template-columns: 1fr;
    }

    .setting-line,
    .compact {
      align-items: stretch;
      display: grid;
      grid-template-columns: 1fr;
    }

    .chat-widget {
      bottom: 14px;
      right: 14px;
    }

    .chat-box {
      height: min(520px, calc(100vh - 88px));
      width: calc(100vw - 28px);
    }

    .chat-input-area {
      flex-direction: column;
    }
  }

  /* Chat Widget Styles */
  .chat-widget {
    bottom: 20px;
    position: fixed;
    right: 20px;
    z-index: 1000;
  }

  .chat-trigger {
    background: #1d4ed8;
    border: none;
    border-radius: 50px;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    color: white;
    cursor: pointer;
    font-size: 14px;
    font-weight: 700;
    min-height: 44px;
    padding: 10px 18px;
    transition: background 0.2s;
  }

  .chat-trigger:hover {
    background: #1e40af;
  }

  .chat-trigger.inline {
    border-radius: 6px;
    box-shadow: none;
  }

  .chat-box {
    background: white;
    border: 1px solid #cbd5e1;
    border-radius: 12px;
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.2);
    display: flex;
    flex-direction: column;
    height: min(520px, calc(100vh - 96px));
    width: min(420px, calc(100vw - 40px));
  }

  .chat-header {
    align-items: center;
    background: #1d4ed8;
    border-top-left-radius: 11px;
    border-top-right-radius: 11px;
    color: white;
    display: flex;
    justify-content: space-between;
    padding: 12px 16px;
  }

  .chat-header h3 {
    font-size: 15px;
    font-weight: 700;
    margin: 0;
  }

  .close-btn {
    background: transparent;
    border: none;
    color: white;
    cursor: pointer;
    font-size: 20px;
    font-weight: 700;
  }

  .chat-messages {
    background: #f8fafc;
    display: flex;
    flex-direction: column;
    flex-grow: 1;
    gap: 12px;
    overflow-y: auto;
    padding: 16px;
  }

  .chat-message {
    display: flex;
    max-width: 80%;
  }

  .chat-message.user {
    align-self: flex-end;
  }

  .chat-message.ai {
    align-self: flex-start;
  }

  .message-bubble {
    border-radius: 8px;
    font-size: 13px;
    line-height: 1.5;
    padding: 8px 12px;
    white-space: pre-wrap;
  }

  .chat-message.user .message-bubble {
    background: #1d4ed8;
    color: white;
  }

  .chat-message.ai .message-bubble {
    background: #e2e8f0;
    color: #1e293b;
  }

  .message-bubble.loading {
    color: #64748b;
    font-style: italic;
  }

  .chat-input-area {
    border-top: 1px solid #e2e8f0;
    display: flex;
    gap: 8px;
    padding: 12px;
  }

  .chat-input-area input {
    border: 1px solid #cbd5e1;
    border-radius: 6px;
    flex-grow: 1;
    font-size: 13px;
    padding: 8px 12px;
  }

  .chat-input-area button {
    background: #1d4ed8;
    border: none;
    border-radius: 6px;
    color: white;
    cursor: pointer;
    font-size: 13px;
    font-weight: 600;
    padding: 8px 16px;
  }

  .chat-input-area button:disabled {
    background: #94a3b8;
    cursor: not-allowed;
  }

  @media (max-width: 900px) {
    .admin-header-actions {
      justify-content: flex-start;
      width: 100%;
    }

    .admin-header-actions button {
      flex: 1 1 120px;
    }

    .chat-widget {
      bottom: 14px;
      right: 14px;
    }

    .chat-box {
      height: min(520px, calc(100vh - 88px));
      width: calc(100vw - 28px);
    }

    .chat-input-area {
      flex-direction: column;
    }
  }
</style>
