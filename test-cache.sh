#!/bin/bash

# Simple test script for the distributed cache API
BASE_URL="http://localhost:18080"

echo "=== Distributed Cache Test ==="
echo

# Test 1: Get user that exists (should load from DB and cache)
echo "1. Getting user 1 (first time - DB load):"
curl -s "$BASE_URL/users/1" | jq .
echo

# Test 2: Get same user again (should be L1/L2 cache hit)
echo "2. Getting user 1 again (cache hit):"
time curl -s "$BASE_URL/users/1" | jq .
echo

# Test 3: Update user (triggers cache invalidation)
echo "3. Updating user 1:"
curl -s -X PUT "$BASE_URL/users/1" \
  -H "Content-Type: application/json" \
  -d '{"name":"Updated Alice","email":"alice.updated@example.com"}'
echo
echo "Update response: OK"

# Test 4: Get updated user (should load from DB due to invalidation)
echo "4. Getting updated user 1:"
curl -s "$BASE_URL/users/1" | jq .
echo

# Test 5: Get non-existent user (bloom filter + null caching)
echo "5. Getting non-existent user 999:"
curl -s "$BASE_URL/users/999" -w "HTTP Status: %{http_code}\n"
echo

# Test 6: Cache metrics
echo "6. Cache metrics:"
curl -s "$BASE_URL/actuator/metrics/cache.gets" | jq .
echo

echo "=== Test Complete ==="