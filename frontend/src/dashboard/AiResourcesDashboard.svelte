<script lang="ts">
  import { onMount } from 'svelte';

  const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

  type Setting = {
    key: string;
    enabled: boolean | null;
    maskedValue: string | null;
    source: 'database' | 'env' | 'none';
  };

  type AiResource = {
    id: string;
    name: string;
    enabled: boolean;
    toolType: string;
    model: string;
    operatorUse: string;
    status: string;
  };

  type SettingRow = {
    key: string;
    label: string;
    kind: 'toggle' | 'secret' | 'text';
    placeholder?: string;
  };

  const settingRows: SettingRow[] = [
    { key: 'google_ai_api_key', label: 'Google AI key', kind: 'secret', placeholder: 'AI Studio API key' },
    { key: 'gemini_api_key', label: 'Gemini fallback key', kind: 'secret', placeholder: 'fallback Gemini key' },
    { key: 'gemini_enabled', label: 'Gemini text', kind: 'toggle' },
    { key: 'gemini_model', label: 'Fast text model', kind: 'text', placeholder: 'gemini-3.5-flash' },
    { key: 'gemini_fallback_models', label: 'Fast fallbacks', kind: 'text', placeholder: 'gemini-3.1-flash-lite,gemini-2.5-flash' },
    { key: 'gemini_pro_model', label: 'Pro model', kind: 'text', placeholder: 'gemini-3.1-pro-preview' },
    { key: 'gemini_pro_fallback_models', label: 'Pro fallbacks', kind: 'text', placeholder: 'gemini-3.5-flash,gemini-2.5-flash' },
    { key: 'google_search_grounding_enabled', label: 'Search grounding', kind: 'toggle' },
    { key: 'url_context_enabled', label: 'URL context', kind: 'toggle' },
    { key: 'design_service_enabled', label: 'Design service', kind: 'toggle' },
    { key: 'nano_banana_enabled', label: 'Nano Banana', kind: 'toggle' },
    { key: 'nano_banana_model', label: 'Image model', kind: 'text', placeholder: 'gemini-3.1-flash-image' },
    { key: 'nano_banana_pro_model', label: 'Pro image model', kind: 'text', placeholder: 'gemini-3-pro-image' },
    { key: 'veo_enabled', label: 'Veo video', kind: 'toggle' },
    { key: 'veo_model', label: 'Veo model', kind: 'text', placeholder: 'veo-3.1-generate-preview' },
    { key: 'antigravity_enabled', label: 'Antigravity', kind: 'toggle' },
    { key: 'antigravity_api_key', label: 'Antigravity key', kind: 'secret', placeholder: 'optional separate key' },
    { key: 'antigravity_agent', label: 'Antigravity agent', kind: 'text', placeholder: 'antigravity-preview-05-2026' },
    { key: 'antigravity_push_enabled', label: 'Diagnostic branch push', kind: 'toggle' }
  ];

  let settings = $state<Setting[]>([]);
  let resources = $state<AiResource[]>([]);
  let drafts = $state<Record<string, string>>({});
  let editing = $state<Record<string, boolean>>({});
  let message = $state('Ready');
  let probing = $state(false);
  let modelProbe = $state<Record<string, any> | null>(null);

  const settingByKey = (key: string) => settings.find((setting) => setting.key === key);

  async function loadResources() {
    try {
      const [settingsResponse, resourcesResponse] = await Promise.all([
        fetch(`${API_BASE}/api/settings`),
        fetch(`${API_BASE}/api/ai/resources`)
      ]);
      if (settingsResponse.ok) {
        settings = await settingsResponse.json();
      }
      if (resourcesResponse.ok) {
        const data = await resourcesResponse.json();
        resources = data.resources || [];
      }
      message = 'Updated';
    } catch (e) {
      message = `Network error: ${e}`;
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
    const saved = await response.json();
    settings = settings.some((setting) => setting.key === saved.key)
      ? settings.map((setting) => setting.key === saved.key ? saved : setting)
      : [...settings, saved];
    editing = { ...editing, [key]: false };
    drafts = { ...drafts, [key]: '' };
    await loadResources();
    message = `${key} saved`;
  }

  async function probeModels() {
    probing = true;
    modelProbe = null;
    try {
      const response = await fetch(`${API_BASE}/api/ai/resources/probe-models`, { method: 'POST' });
      modelProbe = response.ok ? await response.json() : { available: false, status: `HTTP ${response.status}` };
      message = modelProbe?.status || 'Probe finished';
    } catch (e) {
      modelProbe = { available: false, status: 'network_error', message: String(e) };
      message = 'Model probe failed';
    } finally {
      probing = false;
    }
  }

  function startEdit(key: string) {
    editing = { ...editing, [key]: true };
    drafts = { ...drafts, [key]: '' };
  }

  function statusClass(resource: AiResource) {
    if (resource.enabled) return 'ready';
    if ((resource.status || '').includes('missing')) return 'missing';
    return 'disabled';
  }

  onMount(loadResources);
</script>

<section class="resources-shell">
  <div class="resources-header">
    <div>
      <p class="eyebrow">Gemini Resource Layer</p>
      <h2>AI Resources</h2>
    </div>
    <div class="actions">
      <button type="button" onclick={loadResources}>Refresh</button>
      <button type="button" class="secondary" onclick={probeModels} disabled={probing}>
        {probing ? 'Probing...' : 'Probe Models'}
      </button>
    </div>
  </div>

  <section class="resource-grid">
    {#each resources as resource}
      <article class={`resource-card ${statusClass(resource)}`}>
        <div class="resource-top">
          <h3>{resource.name}</h3>
          <span>{resource.enabled ? 'ready' : 'off'}</span>
        </div>
        <p class="tool-type">{resource.toolType}</p>
        <strong>{resource.model || 'not configured'}</strong>
        <small>{resource.operatorUse}</small>
        <p class="resource-status">{resource.status}</p>
      </article>
    {/each}
  </section>

  <section class="settings-panel">
    <div class="panel-head">
      <h2>Resource Settings</h2>
      <span>{settings.length} settings</span>
    </div>

    <div class="settings-table">
      {#each settingRows as row}
        <div class="setting-row">
          <span class="setting-label">{row.label}</span>
          {#if row.kind === 'toggle'}
            <label class="toggle">
              <input
                type="checkbox"
                checked={settingByKey(row.key)?.enabled === true}
                onchange={(event) => saveSetting(row.key, String((event.currentTarget as HTMLInputElement).checked))}
              />
              <span>{settingByKey(row.key)?.enabled === true ? 'enabled' : 'disabled'}</span>
            </label>
          {:else}
            {#if editing[row.key]}
              <input bind:value={drafts[row.key]} type={row.kind === 'secret' ? 'password' : 'text'} placeholder={row.placeholder || row.key} />
            {:else}
              <input value={settingByKey(row.key)?.maskedValue || ''} placeholder={row.placeholder || 'not set'} disabled />
            {/if}
            <button type="button" class="secondary" onclick={() => startEdit(row.key)}>Edit</button>
            <button type="button" onclick={() => saveSetting(row.key, drafts[row.key] || '')} disabled={!editing[row.key]}>
              Save
            </button>
          {/if}
          <small>{settingByKey(row.key)?.source || 'none'}</small>
        </div>
      {/each}
    </div>
  </section>

  {#if modelProbe}
    <section class="settings-panel">
      <div class="panel-head">
        <h2>Model Probe</h2>
        <span>{modelProbe.status}</span>
      </div>
      {#if modelProbe.models}
        <div class="model-list">
          {#each modelProbe.models.slice(0, 24) as model}
            <div class="model-row">
              <strong>{model.name}</strong>
              <small>{model.displayName}</small>
              <span>{(model.supportedGenerationMethods || []).join(', ')}</span>
            </div>
          {/each}
        </div>
      {:else}
        <p class="probe-message">{modelProbe.message || 'No model list returned.'}</p>
      {/if}
    </section>
  {/if}

  <p class="admin-message">Status: {message}</p>
</section>

<style>
  .resources-shell {
    display: grid;
    gap: 18px;
    padding-bottom: 96px;
  }

  .resources-header,
  .resource-top,
  .panel-head,
  .actions {
    align-items: center;
    display: flex;
    gap: 12px;
    justify-content: space-between;
  }

  .actions {
    justify-content: flex-end;
  }

  .resource-grid {
    display: grid;
    gap: 12px;
    grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  }

  .resource-card,
  .settings-panel {
    background: white;
    border: 1px solid #dbe3ef;
    border-radius: 8px;
    padding: 16px;
  }

  .resource-card {
    display: grid;
    gap: 8px;
  }

  .resource-card.ready {
    border-color: #99f6e4;
  }

  .resource-card.missing {
    border-color: #fed7aa;
  }

  .resource-card h3 {
    font-size: 16px;
    margin: 0;
  }

  .resource-card span {
    background: #f1f5f9;
    border-radius: 999px;
    color: #475569;
    font-size: 11px;
    font-weight: 800;
    padding: 4px 8px;
    text-transform: uppercase;
  }

  .resource-card.ready span {
    background: #ccfbf1;
    color: #0f766e;
  }

  .resource-card.missing span {
    background: #ffedd5;
    color: #b45309;
  }

  .tool-type,
  .resource-status,
  .probe-message,
  .admin-message {
    color: #64748b;
    margin: 0;
  }

  .resource-card strong {
    color: #1d4ed8;
  }

  .resource-card small {
    color: #475569;
  }

  .settings-table,
  .model-list {
    display: grid;
    gap: 10px;
    margin-top: 14px;
  }

  .setting-row {
    align-items: center;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 6px;
    display: grid;
    gap: 10px;
    grid-template-columns: 190px minmax(0, 1fr) auto auto 80px;
    padding: 10px;
  }

  .setting-label {
    color: #0f172a;
    font-weight: 800;
  }

  .toggle {
    align-items: center;
    display: flex;
    gap: 8px;
  }

  .toggle input {
    width: auto;
  }

  .secondary {
    background: #475569;
  }

  .model-row {
    align-items: center;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: 6px;
    display: grid;
    gap: 10px;
    grid-template-columns: minmax(220px, 1fr) minmax(160px, 0.8fr) minmax(220px, 1fr);
    padding: 10px;
  }

  .model-row small,
  .model-row span {
    color: #64748b;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  @media (max-width: 900px) {
    .resources-header,
    .panel-head,
    .actions {
      align-items: flex-start;
      flex-direction: column;
    }

    .setting-row,
    .model-row {
      grid-template-columns: 1fr;
    }
  }
</style>
