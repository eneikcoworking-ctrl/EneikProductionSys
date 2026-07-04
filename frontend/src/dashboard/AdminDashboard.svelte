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

  function getStatusColor(integrationName: string): 'success' | 'warning' | 'error' | 'neutral' {
    if (!status) return 'neutral';

    if (integrationName === 'GitHub') {
      if (!status.githubAccess.available) return 'error';
      const ci = status.githubAccess.data?.latest?.ci_status;
      if (ci === 'success') return 'success';
      if (ci === 'failure' || ci === 'error') return 'error';
      if (!ci) return 'neutral';
      return 'warning';
    }

    if (integrationName === 'Linear') {
      if (!status.linearCompleteness.available) return 'error';
      const issues = (status.linearCompleteness.data?.totalIssues as number) ?? 0;
      return issues > 0 ? 'success' : 'neutral';
    }

    if (integrationName === 'Jules') {
      return julesConfigs.length > 0 ? 'success' : 'neutral';
    }

    return 'neutral';
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

  {#if julesConfigs.length > 0 && status?.githubAccess?.available === false}
    <div class="warning-box">
      <div class="warning-header">
        <span class="icon">⚠️</span>
        <strong>Ожидание подтверждения приглашений</strong>
      </div>
      <details>
        <summary>Инструкция по активации Jules-аккаунтов</summary>
        <div class="details-content">
          <p>Некоторые аккаунты Jules ожидают подтверждения приглашения в GitHub репозиторий. Пожалуйста, проверьте почтовые ящики, связанные с аккаунтами, или примите приглашения напрямую через интерфейс GitHub.</p>
          <p>Проект не перейдет в активную фазу, пока все назначенные агенты не подтвердят доступ.</p>
        </div>
      </details>
    </div>
  {/if}

  <section class="admin-panel">
    <div class="panel-head">
      <h2>Интеграции</h2>
      <div class="status-badge {status?.integrations?.available ? 'online' : 'offline'}">
        {status?.integrations?.available ? 'online' : 'degraded'}
      </div>
    </div>
    <div class="integration-grid">
      {#each integrations as integration}
        <article class="integration-card">
          <div class="card-header">
            <div class="card-title-group">
              <h3>{integration.name}</h3>
              <div class="status-dot {getStatusColor(integration.name)}"></div>
            </div>
            <label class="toggle">
              <input
                type="checkbox"
                checked={settingByKey(integration.enabledKey)?.enabled === true}
                onchange={(event) => saveSetting(integration.enabledKey, String((event.currentTarget as HTMLInputElement).checked))}
              />
              <span class="toggle-label">ENABLED</span>
            </label>
          </div>

          <div class="card-content">
            {#if integration.name === 'Jules'}
              <div class="jules-tokens-list">
                {#each julesConfigs as config}
                  <div class="setting-line">
                    <div class="token-info">
                      <span class="token-name-label">{config.name}</span>
                      <small>Account Identity</small>
                    </div>
                    <div class="input-group">
                      {#if editing[`jules_${config.id}`]}
                        <input bind:value={drafts[`jules_${config.id}`]} placeholder="new api key" />
                      {:else}
                        <input value={config.apiKeyMasked} disabled />
                      {/if}
                      <div class="action-buttons">
                        <button type="button" class="secondary mini-btn" onclick={() => {
                          editing[`jules_${config.id}`] = true;
                          drafts[`jules_${config.id}`] = '';
                        }}>Изменить</button>
                        <button type="button" class="mini-btn" onclick={() => updateJulesConfig(config, { apiKey: drafts[`jules_${config.id}`] })} disabled={!editing[`jules_${config.id}`]}>
                          Сохранить
                        </button>
                        <button type="button" class="danger mini-btn" onclick={() => deleteJulesConfig(config.id)}>×</button>
                      </div>
                    </div>
                    <label class="toggle mini">
                      <input type="checkbox" checked={config.enabled} onchange={(e) => updateJulesConfig(config, { enabled: e.currentTarget.checked })} />
                    </label>
                  </div>
                {/each}
              </div>
            {:else}
              <div class="setting-line-simple">
                <div class="label-group">
                  <span class="field-label">{integration.name === 'GitHub' ? 'GitHub username' : 'Token'}</span>
                  <small>API ACCESS</small>
                </div>
                <div class="input-row">
                  {#if editing[integration.secretKey]}
                    <input bind:value={drafts[integration.secretKey]} placeholder="new token" />
                  {:else}
                    <input value={settingByKey(integration.secretKey)?.maskedValue || ''} placeholder="not set" disabled />
                  {/if}
                  <button type="button" class="secondary mini-btn" onclick={() => startEdit(integration.secretKey)}>Изменить</button>
                  <button type="button" class="mini-btn" onclick={() => saveSetting(integration.secretKey, drafts[integration.secretKey] || '')} disabled={!editing[integration.secretKey]}>
                    Сохранить
                  </button>
                </div>
              </div>
            {/if}

            {#if integration.extraKey}
              <div class="setting-line-simple">
                <div class="label-group">
                  <span class="field-label">Team ID</span>
                  <small>LINEAR SCOPE</small>
                </div>
                <div class="input-row">
                  {#if editing[integration.extraKey]}
                    <input bind:value={drafts[integration.extraKey]} placeholder="Linear team id" />
                  {:else}
                    <input value={settingByKey(integration.extraKey)?.maskedValue || ''} placeholder="not set" disabled />
                  {/if}
                  <button type="button" class="secondary mini-btn" onclick={() => startEdit(integration.extraKey)}>Изменить</button>
                  <button type="button" class="mini-btn" onclick={() => saveSetting(integration.extraKey, drafts[integration.extraKey] || '')} disabled={!editing[integration.extraKey]}>
                    Сохранить
                  </button>
                </div>
              </div>
            {/if}
          </div>

          <div class="card-footer">
            <span class="source-tag">{settingByKey(integration.secretKey)?.source || 'none'}</span>
            {#if integration.name === 'GitHub'}
              <div class="metric-tag">
                <span class="status-indicator {status?.githubAccess?.data?.latest?.ci_status === 'success' ? 'success' : 'neutral'}"></span>
                {status?.githubAccess?.data?.latest?.ci_status || 'no check yet'}
              </div>
            {:else if integration.name === 'Linear'}
              <div class="metric-tag">
                {status?.linearCompleteness?.data?.totalIssues ?? 0} issues detected
              </div>
            {:else}
              <div class="metric-tag">
                {status?.julesSessions?.data?.total ?? 0} active sessions
              </div>
            {/if}
          </div>
        </article>
      {/each}
    </div>

    <div class="add-token-section">
      <h3>Добавить новый Jules-аккаунт</h3>
      <div class="add-token-grid">
        <div class="input-box">
          <label for="acc-name">Account Name</label>
          <input id="acc-name" bind:value={newJulesToken.name} placeholder="e.g. Jules-01" />
        </div>
        <div class="input-box">
          <label for="acc-key">API Key</label>
          <input id="acc-key" bind:value={newJulesToken.apiKey} placeholder="sk-..." type="password" />
        </div>
        <button type="button" class="add-btn" onclick={addJulesConfig} disabled={!newJulesToken.name || !newJulesToken.apiKey}>Add Account</button>
      </div>
    </div>
  </section>

  <section class="admin-panel">
    <div class="panel-head">
      <h2>Аккаунты</h2>
      <div class="status-badge info">{status?.accounts?.data?.total ?? 0} agents attached</div>
    </div>
    <div class="account-summary">
      <span class="status-pill idle">{status?.accounts?.data?.idle ?? 0} idle</span>
      <span class="status-pill busy">{status?.accounts?.data?.busy ?? 0} busy</span>
      <span class="status-pill offline">{status?.accounts?.data?.offline ?? 0} offline</span>
    </div>
    <div class="account-table-wrapper">
      <table class="account-table">
        <thead>
          <tr>
            <th>Name</th>
            <th>Status</th>
            <th>Token</th>
            <th>Project</th>
            <th>Capabilities</th>
            <th>Heartbeat</th>
          </tr>
        </thead>
        <tbody>
          {#each status?.accounts?.data?.items || [] as account}
            <tr class="account-row">
              <td class="font-bold">{account.name}</td>
              <td><span class="pill {account.status}">{account.status}</span></td>
              <td class="text-xs text-muted">{account.julesConfigName || 'default'}</td>
              <td class="text-xs">{account.currentProjectId || '—'}</td>
              <td>
                <div class="capabilities-list">
                  {#each account.capabilities.split(',') as cap}
                    {#if cap.trim()}
                      <span class="cap-chip">{cap.trim()}</span>
                    {/if}
                  {/each}
                </div>
              </td>
              <td class="text-xs text-muted">{formatDate(account.lastHeartbeat)}</td>
            </tr>
          {/each}
        </tbody>
      </table>
    </div>
  </section>

  <section class="admin-panel compact-footer">
    <div class="summary-grid">
      <div class="summary-item">
        <small>GITHUB STATUS</small>
        <p>{status?.githubAccess?.available ? (status?.githubAccess?.data?.latest?.ci_status || 'no check') : 'unavailable'}</p>
      </div>
      <div class="summary-item">
        <small>LINEAR HEALTH</small>
        <p>{Math.round(((status?.linearCompleteness?.data?.completeness_rate as number) || 0) * 100)}% complete</p>
      </div>
      <div class="summary-item">
        <small>SYSTEM STABILITY</small>
        <p>{status?.julesSessions?.data?.stuck ?? 0} stuck sessions</p>
      </div>
    </div>
    <p class="admin-message">{message}</p>
  </section>
</section>

<style>
  .admin-shell {
    display: grid;
    gap: var(--space-6);
    padding-bottom: var(--space-8);
  }

  .admin-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-end;
  }

  .admin-grid.overview {
    display: grid;
    gap: var(--space-4);
    grid-template-columns: repeat(4, 1fr);
  }

  .stat {
    background: var(--surface);
    border: 1px solid var(--neutral-200);
    border-radius: 12px;
    padding: var(--space-4);
    box-shadow: 0 1px 3px rgba(0,0,0,0.05);
  }

  .stat span {
    display: block;
    font-size: 32px;
    font-weight: 800;
    color: var(--neutral-800);
  }

  .stat p {
    color: var(--neutral-500);
    font-size: 14px;
    font-weight: 600;
    text-transform: uppercase;
    margin-top: var(--space-1);
  }

  .warning-box {
    background: var(--warning-bg);
    border: 1px solid var(--warning);
    border-radius: 8px;
    padding: var(--space-4);
  }

  .warning-header {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    color: var(--warning);
    margin-bottom: var(--space-2);
  }

  .warning-box details {
    color: var(--warning);
    font-size: 14px;
  }

  .warning-box summary {
    cursor: pointer;
    font-weight: 600;
  }

  .details-content {
    margin-top: var(--space-2);
    padding-left: var(--space-3);
    border-left: 2px solid var(--warning);
  }

  .admin-panel {
    background: var(--surface);
    border: 1px solid var(--neutral-200);
    border-radius: 12px;
    padding: var(--space-6);
    box-shadow: 0 1px 3px rgba(0,0,0,0.05);
  }

  .panel-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: var(--space-4);
  }

  .status-badge {
    padding: var(--space-1) var(--space-3);
    border-radius: 999px;
    font-size: 12px;
    font-weight: 700;
    text-transform: uppercase;
  }

  .status-badge.online { background: var(--success-bg); color: var(--success); }
  .status-badge.offline { background: var(--error-bg); color: var(--error); }
  .status-badge.info { background: var(--neutral-100); color: var(--neutral-600); }

  .integration-grid {
    display: grid;
    gap: var(--space-4);
    grid-template-columns: repeat(3, 1fr);
  }

  .integration-card {
    background: var(--neutral-50);
    border: 1px solid var(--neutral-200);
    border-radius: 10px;
    display: flex;
    flex-direction: column;
    min-height: 280px;
  }

  .card-header {
    padding: var(--space-4);
    border-bottom: 1px solid var(--neutral-200);
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .card-title-group {
    display: flex;
    align-items: center;
    gap: var(--space-2);
  }

  .card-title-group h3 {
    margin: 0;
    font-size: 18px;
    font-weight: 700;
  }

  .status-dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
  }
  .status-dot.success { background: var(--success); }
  .status-dot.neutral { background: var(--neutral-400); }
  .status-dot.warning { background: var(--warning); }
  .status-dot.error { background: var(--error); }

  .toggle {
    display: flex;
    align-items: center;
    gap: var(--space-1);
    cursor: pointer;
  }
  .toggle-label { font-size: 10px; font-weight: 800; color: var(--neutral-500); }

  .card-content {
    flex: 1;
    padding: var(--space-4);
  }

  .jules-tokens-list {
    max-height: 200px;
    overflow-y: auto;
  }

  .setting-line {
    display: grid;
    grid-template-columns: 1fr auto;
    gap: var(--space-3);
    margin-bottom: var(--space-4);
    padding-bottom: var(--space-3);
    border-bottom: 1px dashed var(--neutral-200);
  }

  .token-info { display: flex; flex-direction: column; }
  .token-name-label { font-weight: 700; font-size: 14px; }
  .token-info small { font-size: 10px; color: var(--neutral-400); text-transform: uppercase; }

  .input-group { display: flex; flex-direction: column; gap: var(--space-2); }
  .action-buttons { display: flex; gap: var(--space-1); }

  .setting-line-simple {
    margin-bottom: var(--space-4);
  }

  .label-group { display: flex; flex-direction: column; margin-bottom: var(--space-1); }
  .field-label { font-weight: 600; font-size: 14px; }
  .label-group small { font-size: 10px; color: var(--neutral-400); font-weight: 700; }

  .input-row { display: flex; gap: var(--space-2); }

  .mini-btn {
    padding: 0 var(--space-2);
    min-height: 32px;
    font-size: 12px;
  }

  .card-footer {
    padding: var(--space-3) var(--space-4);
    background: var(--neutral-100);
    border-top: 1px solid var(--neutral-200);
    border-radius: 0 0 10px 10px;
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .source-tag { font-size: 11px; font-weight: 700; color: var(--neutral-400); text-transform: uppercase; }
  .metric-tag { font-size: 12px; font-weight: 600; color: var(--neutral-600); display: flex; align-items: center; gap: var(--space-1); }
  .status-indicator { width: 8px; height: 8px; border-radius: 50%; }
  .status-indicator.success { background: var(--success); }

  .add-token-section {
    margin-top: var(--space-8);
    padding-top: var(--space-6);
    border-top: 2px solid var(--neutral-100);
  }

  .add-token-section h3 { margin: 0 0 var(--space-4); font-size: 16px; color: var(--neutral-800); }

  .add-token-grid {
    display: grid;
    grid-template-columns: 1fr 1fr auto;
    gap: var(--space-4);
    align-items: flex-end;
  }

  .input-box label { display: block; font-size: 12px; font-weight: 700; color: var(--neutral-500); margin-bottom: var(--space-1); text-transform: uppercase; }

  .add-btn { background: var(--neutral-800); }

  .account-summary { display: flex; gap: var(--space-3); margin-bottom: var(--space-4); }
  .status-pill { padding: 6px 14px; border-radius: 6px; font-size: 13px; font-weight: 700; }
  .status-pill.idle { background: var(--success-bg); color: var(--success); }
  .status-pill.busy { background: var(--warning-bg); color: var(--warning); }
  .status-pill.offline { background: var(--error-bg); color: var(--error); }

  .account-table-wrapper { overflow-x: auto; border: 1px solid var(--neutral-200); border-radius: 8px; }
  .account-table { width: 100%; border-collapse: collapse; text-align: left; }
  .account-table th { padding: var(--space-3) var(--space-4); background: var(--neutral-50); border-bottom: 1px solid var(--neutral-200); font-size: 11px; color: var(--neutral-500); text-transform: uppercase; font-weight: 800; }
  .account-table td { padding: 14px 16px; border-bottom: 1px solid var(--neutral-100); font-size: 14px; }

  .capabilities-list { display: flex; flex-direction: column; gap: var(--space-1); }
  .cap-chip { background: var(--neutral-100); color: var(--neutral-600); padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; width: fit-content; text-transform: none; }

  .compact-footer { display: flex; justify-content: space-between; align-items: center; padding: var(--space-4) var(--space-6); }
  .summary-grid { display: flex; gap: var(--space-8); }
  .summary-item small { display: block; font-size: 10px; font-weight: 800; color: var(--neutral-400); margin-bottom: 2px; }
  .summary-item p { font-size: 14px; font-weight: 700; color: var(--neutral-700); }

  .font-bold { font-weight: 700; }
  .text-xs { font-size: 12px; }
  .text-muted { color: var(--neutral-500); }

  @media (max-width: 1200px) {
    .integration-grid { grid-template-columns: repeat(2, 1fr); }
  }

  @media (max-width: 768px) {
    .admin-grid.overview, .integration-grid, .add-token-grid { grid-template-columns: 1fr; }
    .compact-footer { flex-direction: column; gap: var(--space-4); align-items: flex-start; }
    .summary-grid { flex-wrap: wrap; gap: var(--space-4); }
  }
</style>
