<script lang="ts">
  /**
   * @file HelloWorld.svelte
   * @agent TAG-03 (Belief Intension) & TAG-11 (Client Perception)
   * @description The phenomenal interface for the Hello World project (Svelte edition).
   */
  export let message: string;
</script>

<div class="container">
  <h1 class="title">{message}</h1>
  <p class="subtitle">Phenomenal experience optimized (LCP &lt; 2.5s)</p>
</div>

<style>
  .container {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    height: 100vh;
    background-color: #f0f2f5;
    font-family: 'Inter', sans-serif;
  }

  .title {
    font-size: 3rem;
    color: #1a1a1a;
    text-align: center;
  }

  .subtitle {
    font-size: 1.2rem;
    color: #666;
    margin-top: 1rem;
  }
</style>
