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
    status: 'idle' | 'busy' | 'offline';
    currentProjectId?: string;
    capabilities: string;
    lastHeartbeat?: string;
    julesConfigName?: string;
  };

  type JulesConfig = {
    id: string;
    name: string;
    apiKeyMasked: string;
    enabled: boolean;
  };

  type AccountsData = {
    total: number;
    idle: number;
    busy: number;
    offline: number;
    items: AccountItem[];
  };

  type SystemStatus = {
    integrations: Section<Setting[]>;
    accounts: Section<AccountsData>;
    githubAccess: Section<{ latest: Record<string, unknown> | null }>;
    linearCompleteness: Section<Record<string, unknown>>;
    julesSessions: Section<Record<string, number>>;
    qualityGate: Section<Record<string, number>>;
    tasks: Section<Record<string, number>>;
  };

  const integrations = [
    { name: 'GitHub', enabledKey: 'github_enabled', secretKey: 'github_token' },
    { name: 'Linear', enabledKey: 'linear_enabled', secretKey: 'linear_api_key', extraKey: 'linear_team_id' },
    { name: 'Jules', enabledKey: 'jules_enabled', secretKey: 'jules_api_key' }
  ];

  let status = $state<SystemStatus | null>(null);
  let settings = $state<Setting[]>([]);
  let julesConfigs = $state<JulesConfig[]>([]);
  let drafts = $state<Record<string, string>>({});
  let editing = $state<Record<string, boolean>>({});
  let message = $state('Ready');

  let newJulesToken = $state({ name: '', apiKey: '' });

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
    await loadJulesConfigs();
    message = 'Updated';
  }

  async function loadJulesConfigs() {
    const response = await fetch(`${API_BASE}/api/jules-configs`);
    if (response.ok) {
      julesConfigs = await response.json();
    }
  }

  async function addJulesConfig() {
    if (!newJulesToken.name || !newJulesToken.apiKey) return;
    const response = await fetch(`${API_BASE}/api/jules-configs`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(newJulesToken)
    });
    if (response.ok) {
      newJulesToken = { name: '', apiKey: '' };
      await loadJulesConfigs();
      message = 'Token added';
    }
  }

  async function updateJulesConfig(config: JulesConfig, updates: Partial<JulesConfig & { apiKey?: string }>) {
    const response = await fetch(`${API_BASE}/api/jules-configs/${config.id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(updates)
    });
    if (response.ok) {
      editing[`jules_${config.id}`] = false;
      drafts[`jules_${config.id}`] = '';
      await loadJulesConfigs();
      message = 'Token updated';
    }
  }

  async function deleteJulesConfig(id: string) {
    if (!confirm('Are you sure?')) return;
    const response = await fetch(`${API_BASE}/api/jules-configs/${id}`, {
      method: 'DELETE'
    });
    if (response.ok) {
      await loadJulesConfigs();
      message = 'Token deleted';
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
    <div class="stat">
      <span>{status?.tasks?.data?.queued ?? 0}</span>
      <p>Queued tasks</p>
    </div>
    <div class="stat">
      <span>{status?.julesSessions?.data?.running ?? 0}</span>
      <p>Jules running</p>
    </div>
    <div class="stat">
      <span>{status?.julesSessions?.data?.failed ?? 0}</span>
      <p>Jules failed</p>
    </div>
    <div class="stat">
      <span>{Math.round(status?.qualityGate?.data?.dpmo ?? 0)}</span>
      <p>Gate DPMO</p>
    </div>
  </section>

  <section class="admin-panel">
    <div class="panel-head">
      <h2>Интеграции</h2>
      <span>{status?.integrations?.available ? 'online' : 'degraded'}</span>
    </div>
    <div class="integration-grid">
      {#each integrations as integration}
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

          {#if integration.name === 'Jules'}
            <div class="jules-tokens-list">
              {#each julesConfigs as config}
                <div class="setting-line">
                  <span class="token-name-label">{config.name}</span>
                  {#if editing[`jules_${config.id}`]}
                    <input bind:value={drafts[`jules_${config.id}`]} placeholder="new api key" />
                  {:else}
                    <input value={config.apiKeyMasked} disabled />
                  {/if}
                  <button type="button" class="secondary" onclick={() => {
                    editing[`jules_${config.id}`] = true;
                    drafts[`jules_${config.id}`] = '';
                  }}>Изменить</button>
                  <button type="button" onclick={() => updateJulesConfig(config, { apiKey: drafts[`jules_${config.id}`] })} disabled={!editing[`jules_${config.id}`]}>
                    Сохранить
                  </button>
                  <button type="button" class="danger" onclick={() => deleteJulesConfig(config.id)}>×</button>
                  <label class="toggle mini">
                    <input type="checkbox" checked={config.enabled} onchange={(e) => updateJulesConfig(config, { enabled: e.currentTarget.checked })} />
                  </label>
                </div>
              {/each}
              <div class="setting-line add-token">
                <input bind:value={newJulesToken.name} placeholder="Account Name (e.g. Jules-01)" />
                <input bind:value={newJulesToken.apiKey} placeholder="API Key" />
                <button type="button" onclick={addJulesConfig} disabled={!newJulesToken.name || !newJulesToken.apiKey}>Add Token</button>
              </div>
            </div>
          {:else}
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
          {/if}

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
      <h2>Аккаунты</h2>
      <span>{status?.accounts?.data?.total ?? 0} total</span>
    </div>
    <div class="account-summary">
      <span class="idle">{status?.accounts?.data?.idle ?? 0} idle</span>
      <span class="busy">{status?.accounts?.data?.busy ?? 0} busy</span>
      <span class="offline">{status?.accounts?.data?.offline ?? 0} offline</span>
    </div>
    <div class="account-table">
      <div class="table-head">
        <span>Name</span>
        <span>Status</span>
        <span>Token</span>
        <span>Project</span>
        <span>Capabilities</span>
        <span>Heartbeat</span>
      </div>
      {#each status?.accounts?.data?.items || [] as account}
        <div class="table-row">
          <strong>{account.name}</strong>
          <span class={`pill ${account.status}`}>{account.status}</span>
          <span class="text-xs">{account.julesConfigName || 'default'}</span>
          <span>{account.currentProjectId || 'none'}</span>
          <span>{account.capabilities}</span>
          <span>{formatDate(account.lastHeartbeat)}</span>
        </div>
      {/each}
    </div>
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
    grid-template-columns: repeat(4, minmax(140px, 1fr));
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

  .jules-tokens-list .setting-line {
    grid-template-columns: 100px minmax(0, 1fr) auto auto auto auto;
    margin-bottom: 8px;
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

  .table-head,
  .table-row {
    display: grid;
    gap: 12px;
    grid-template-columns: minmax(160px, 1fr) 90px 100px minmax(150px, 1fr) minmax(220px, 1.4fr) minmax(160px, 1fr);
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
