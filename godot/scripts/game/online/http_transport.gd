extends Node
## HTTP transport for game_api.gd (live GameHttpTransport): one HTTPRequest per call,
## 35 s overall timeout (live callTimeout), no redirects (followRedirects=false), response
## capped just above the API's 512 KB limit so oversized bodies are rejected early.

const CALL_TIMEOUT_S := 35.0
const EXIT_CALL_TIMEOUT_S := 9.0     # live exitClient
const BODY_LIMIT := 512 * 1024 + 1

var call_timeout := CALL_TIMEOUT_S

## Returns {status, body}; {status: 0, error} for a network failure; {status: -1} when the
## response exceeds the size limit.
func send(method: String, url: String, headers: PackedStringArray, body: String) -> Dictionary:
	var http := HTTPRequest.new()
	http.timeout = call_timeout
	http.max_redirects = 0
	http.body_size_limit = BODY_LIMIT
	http.use_threads = true
	add_child(http)
	var m: int = {"GET": HTTPClient.METHOD_GET, "POST": HTTPClient.METHOD_POST, "PUT": HTTPClient.METHOD_PUT,
		"DELETE": HTTPClient.METHOD_DELETE}.get(method, HTTPClient.METHOD_GET)
	var err := http.request(url, headers, m, body)
	if err != OK:
		http.queue_free()
		return {"status": 0, "error": "NETWORK_ERROR"}
	var result: Array = await http.request_completed
	http.queue_free()
	var code: int = result[0]
	if code == HTTPRequest.RESULT_BODY_SIZE_LIMIT_EXCEEDED:
		return {"status": -1, "error": "RESPONSE_TOO_LARGE"}
	if code != HTTPRequest.RESULT_SUCCESS:
		return {"status": 0, "error": "TIMEOUT" if code == HTTPRequest.RESULT_TIMEOUT else "NETWORK_ERROR"}
	return {"status": int(result[1]), "body": (result[3] as PackedByteArray).get_string_from_utf8()}
