<script lang="ts">
  import { onMount } from 'svelte';
  import type { LeanGreeting } from '../lib/types';

  type ApiError = {
    error: string;
    code: number;
  };

  const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;

  let data: LeanGreeting | null = null;
  let isLoading = true;
  let errorMessage: string | null = null;
  let newMessage = '';
  let isSubmitting = false;

  async function loadPipelineStatus() {
    isLoading = true;
    errorMessage = null;
    try {
      const response = await fetch(`${API_BASE_URL}/api/v1/greetings/latest`);
      if (!response.ok) {
        if (response.status === 404) {
          data = null;
          return;
        }
        throw new Error(`Failed to fetch status: ${response.statusText}`);
      }
      data = await response.json();
    } catch (err) {
      errorMessage = err instanceof Error ? err.message : 'An unknown error occurred';
    } finally {
      isLoading = false;
    }
  }

  async function sendGreeting() {
    if (!newMessage.trim()) return;

    isSubmitting = true;
    errorMessage = null;
    try {
      const response = await fetch(`${API_BASE_URL}/api/v1/greetings`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ message: newMessage }),
      });

      if (!response.ok) {
        const errorData: ApiError = await response.json();
        throw new Error(errorData.error || `Error: ${response.status}`);
      }

      newMessage = '';
      await loadPipelineStatus();
    } catch (err) {
      errorMessage = err instanceof Error ? err.message : 'Failed to send greeting';
    } finally {
      isSubmitting = false;
    }
  }

  function formatLeadTime(seconds: number): string {
    return `Обработано за ${seconds} сек`;
  }

  onMount(() => {
    loadPipelineStatus();
  });

  const stages = [
    { key: 'RECEIVED', label: 'Принято' },
    { key: 'IN_PROGRESS', label: 'В работе' },
    { key: 'COMPLETED', label: 'Готово' }
  ];

</script>

<div class="hello-world" class:blocked={data?.currentStatus === 'BLOCKED'}>
  <h1>Value Stream Pipeline</h1>

  {#if isLoading}
    <div class="loading">Загрузка...</div>
  {:else if errorMessage}
    <div class="error">Ошибка: {errorMessage}</div>
  {/if}

  {#if data}
    <div class="pipeline-status">
      <div class="pipeline-container">
        {#each stages as stage, i}
          <div class="stage" class:active={data.currentStatus === stage.key}>
            <div class="stage-label">{stage.label}</div>
            <div class="stage-indicator"></div>
          </div>
          {#if i < stages.length - 1}
            <div class="connector"></div>
          {/if}
        {/each}
      </div>

      <div class="greeting-details">
        <p><strong>Сообщение:</strong> {data.message}</p>
        <p><strong>Статус:</strong> {data.currentStatus}</p>
        <p class="lead-time">{formatLeadTime(data.leadTimeSeconds)}</p>
      </div>
    </div>
  {:else if !isLoading}
    <p>Нет активных данных.</p>
  {/if}

  <form on:submit|preventDefault={sendGreeting} class="greeting-form">
    <input
      type="text"
      bind:value={newMessage}
      placeholder="Введите пожелание..."
      disabled={isSubmitting}
    />
    <button type="submit" disabled={isSubmitting || !newMessage.trim()}>
      {isSubmitting ? 'Отправка...' : 'Отправить'}
    </button>
  </form>
</div>

<style>
  .hello-world {
    padding: 2rem;
    max-width: 600px;
    margin: 0 auto;
    border: 1px solid #ccc;
    border-radius: 8px;
    background-color: #fff;
    transition: background-color 0.3s;
  }

  .hello-world.blocked {
    background-color: #fff1f1;
    border-color: #ff4d4f;
  }

  .error {
    color: #ff4d4f;
    margin-bottom: 1rem;
    padding: 0.5rem;
    background: #fff1f0;
    border: 1px solid #ffccc7;
    border-radius: 4px;
  }

  .pipeline-container {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin: 2rem 0;
  }

  .stage {
    display: flex;
    flex-direction: column;
    align-items: center;
    flex: 1;
  }

  .stage-label {
    font-size: 0.85rem;
    margin-bottom: 0.5rem;
    color: #888;
  }

  .stage.active .stage-label {
    color: #1890ff;
    font-weight: bold;
  }

  .blocked .stage.active .stage-label {
    color: #ff4d4f;
  }

  .stage-indicator {
    width: 20px;
    height: 20px;
    border-radius: 50%;
    border: 2px solid #ddd;
    background-color: #fff;
  }

  .stage.active .stage-indicator {
    background-color: #1890ff;
    border-color: #1890ff;
    box-shadow: 0 0 8px rgba(24, 144, 255, 0.5);
  }

  .blocked .stage-indicator {
     border-color: #ff4d4f;
  }

  .blocked .stage.active .stage-indicator {
    background-color: #ff4d4f;
    border-color: #ff4d4f;
    box-shadow: 0 0 8px rgba(255, 77, 79, 0.5);
  }

  .connector {
    height: 2px;
    background-color: #ddd;
    flex: 1;
    margin: 0 10px;
    margin-top: 18px; /* Align with indicators */
  }

  .greeting-details {
    margin-top: 1rem;
    border-top: 1px solid #eee;
    padding-top: 1rem;
  }

  .lead-time {
    font-style: italic;
    color: #555;
  }

  .greeting-form {
    margin-top: 2rem;
    display: flex;
    gap: 0.5rem;
  }

  .greeting-form input {
    flex: 1;
    padding: 0.5rem;
    border: 1px solid #ccc;
    border-radius: 4px;
  }

  .greeting-form button {
    padding: 0.5rem 1rem;
    background-color: #1890ff;
    color: white;
    border: none;
    border-radius: 4px;
    cursor: pointer;
  }

  .greeting-form button:disabled {
    background-color: #ccc;
    cursor: not-allowed;
  }
</style>
