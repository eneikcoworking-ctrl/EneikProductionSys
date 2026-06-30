<script lang="ts">
  export let pipeline = { queued: 0, claimed: 0, in_progress: 0, review: 0, done: 0, failed: 0 };
</script>

<div class="p-4 bg-white shadow rounded-lg">
  <h2 class="text-xl font-bold mb-4">Pipeline</h2>
  <div class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4 text-center">
    <div class="p-2 bg-gray-50 rounded">
      <div class="text-xs uppercase text-gray-500">Queued</div>
      <div class="text-2xl font-bold">{pipeline.queued}</div>
    </div>
    <div class="p-2 bg-blue-50 rounded">
      <div class="text-xs uppercase text-gray-500">Claimed</div>
      <div class="text-2xl font-bold text-blue-600">{pipeline.claimed}</div>
    </div>
    <div class="p-2 bg-indigo-50 rounded">
      <div class="text-xs uppercase text-gray-500">In Progress</div>
      <div class="text-2xl font-bold text-indigo-600">{pipeline.in_progress}</div>
    </div>
    <div class="p-2 bg-yellow-50 rounded">
      <div class="text-xs uppercase text-gray-500">Review</div>
      <div class="text-2xl font-bold text-yellow-600">{pipeline.review}</div>
    </div>
    <div class="p-2 bg-green-50 rounded">
      <div class="text-xs uppercase text-gray-500">Done</div>
      <div class="text-2xl font-bold text-green-600">{pipeline.done}</div>
    </div>
    <div class="p-2 bg-red-50 rounded">
      <div class="text-xs uppercase text-gray-500">Failed</div>
      <div class="text-2xl font-bold text-red-600">{pipeline.failed}</div>
    </div>
  </div>
</div>
