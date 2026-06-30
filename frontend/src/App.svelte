<script>
  let greeting = { message: 'Loading...' };

  async function fetchGreeting() {
    try {
      const response = await fetch('/api/v1/greetings/latest');
      greeting = await response.json();
    } catch (e) {
      greeting = { message: 'Error connecting to backend' };
    }
  }

  fetchGreeting();
</script>

<main>
  <h1>Eneik Production System</h1>
  <p>Status: {greeting.message}</p>
</main>
