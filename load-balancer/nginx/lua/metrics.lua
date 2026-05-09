-- Payment System Load Balancer - Metrics Collection
-- Lua module for collecting and exposing nginx metrics

local _M = {}

-- Collect nginx metrics
function _M.collect()
    -- Get nginx stub status
    local status = ngx.location.capture("/nginx_status")

    if status.status ~= 200 then
        ngx.status = 500
        ngx.say('{"error": "Failed to get nginx status"}')
        return
    end

    -- Parse stub status output
    local body = status.body
    local active, accepted, handled, requests, reading, writing, waiting = body:match(
        "Active connections: (%d+)%s*server accepts handled requests%s*(%d+) (%d+) (%d+)%s*Reading: (%d+) Writing: (%d+) Waiting: (%d+)"
    )

    -- Return metrics in JSON format
    local metrics = {
        timestamp = ngx.time(),
        active_connections = tonumber(active),
        total_accepted = tonumber(accepted),
        total_handled = tonumber(handled),
        total_requests = tonumber(requests),
        reading = tonumber(reading),
        writing = tonumber(writing),
        waiting = tonumber(waiting),
        nginx_version = ngx.var.nginx_version,
        hostname = ngx.var.hostname
    }

    ngx.header.content_type = "application/json"
    ngx.say(ngx.encode_json(metrics))
end

return _M