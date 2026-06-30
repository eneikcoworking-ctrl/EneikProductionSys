<script>
  /**
   * @file HelloWorld.svelte
   * @agent TAG-03 (Belief Intension) & TAG-11 (Client Perception)
   * @description The phenomenal Svelte interface for the Hello World project.
   */
  export let message = "Loading...";
  export let status = "UNKNOWN";
</script>

<main>
  <h1>{message}</h1>
  <p>Status: <span class="status">{status}</span></p>
  <p class="optimized">Phenomenal experience optimized (LCP &lt; 2.5s)</p>
</main>

<style>
  main {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    height: 100vh;
    background-color: #f0f2f5;
    font-family: 'Inter', sans-serif;
  }
  h1 {
    font-size: 3rem;
    color: #1a1a1a;
    text-align: center;
  }
  .status {
    font-weight: bold;
    color: #007bff;
  }
  .optimized {
    font-size: 1.2rem;
    color: #666;
    margin-top: 1rem;
  }
</style>
