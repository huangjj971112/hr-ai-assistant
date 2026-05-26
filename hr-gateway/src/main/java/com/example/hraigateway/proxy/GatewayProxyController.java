package com.example.hraigateway.proxy;

import com.example.hraigateway.config.GatewayProperties;
import com.example.hraigateway.security.GatewayAuthFilter;
import com.example.hraigateway.security.GatewayUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.NoOpResponseErrorHandler;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class GatewayProxyController {

    private static final List<String> SKIPPED_HEADERS = List.of(
            HttpHeaders.CONNECTION,
            HttpHeaders.HOST,
            HttpHeaders.CONTENT_LENGTH,
            "Keep-Alive",
            HttpHeaders.PROXY_AUTHENTICATE,
            HttpHeaders.PROXY_AUTHORIZATION,
            HttpHeaders.TE,
            HttpHeaders.TRAILER,
            HttpHeaders.TRANSFER_ENCODING
    );

    private final GatewayProperties gatewayProperties;
    private final RestTemplate restTemplate = createRestTemplate();

    @RequestMapping(value = "/api/employee/handbook/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> proxyStream(
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body
    ) {
        URI targetUri = buildTargetUri(request);
        HttpHeaders headers = buildBackendHeaders(request);

        StreamingResponseBody stream = outputStream -> {
            RequestCallback requestCallback = backendRequest -> {
                backendRequest.getHeaders().putAll(headers);
                if (body != null) {
                    StreamUtils.copy(body, backendRequest.getBody());
                }
            };
            ResponseExtractor<Void> responseExtractor = backendResponse -> {
                StreamUtils.copy(backendResponse.getBody(), outputStream);
                outputStream.flush();
                return null;
            };
            restTemplate.execute(
                    targetUri,
                    HttpMethod.valueOf(request.getMethod()),
                    requestCallback,
                    responseExtractor
            );
        };

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.TEXT_EVENT_STREAM);
        responseHeaders.setCacheControl("no-cache");
        return ResponseEntity.ok().headers(responseHeaders).body(stream);
    }

    @RequestMapping("/api/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request, @RequestBody(required = false) byte[] body) {
        URI targetUri = buildTargetUri(request);
        HttpHeaders headers = buildBackendHeaders(request);

        ResponseEntity<byte[]> backendResponse = restTemplate.exchange(
                targetUri,
                HttpMethod.valueOf(request.getMethod()),
                new HttpEntity<>(body, headers),
                byte[].class
        );

        HttpHeaders responseHeaders = new HttpHeaders();
        backendResponse.getHeaders().forEach((name, values) -> {
            if (SKIPPED_HEADERS.stream().noneMatch(skipped -> skipped.equalsIgnoreCase(name))) {
                responseHeaders.put(name, values);
            }
        });
        return new ResponseEntity<>(backendResponse.getBody(), responseHeaders, backendResponse.getStatusCode());
    }

    private URI buildTargetUri(HttpServletRequest request) {
        return URI.create(gatewayProperties.getBackendBaseUrl() + request.getRequestURI()
                + (request.getQueryString() == null ? "" : "?" + request.getQueryString()));
    }

    private HttpHeaders buildBackendHeaders(HttpServletRequest request) {
        HttpHeaders headers = copyHeaders(request);
        GatewayUser user = (GatewayUser) request.getAttribute(GatewayAuthFilter.USER_REQUEST_ATTRIBUTE);
        if (user != null) {
            headers.set("X-User-Id", String.valueOf(user.userId()));
            headers.set("X-Username", user.username());
            headers.set("X-Employee-Name", URLEncoder.encode(user.employeeName(), StandardCharsets.UTF_8));
            headers.set("X-User-Role", user.role());
        }
        return headers;
    }

    private HttpHeaders copyHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        Collections.list(request.getHeaderNames()).forEach(name -> {
            if (SKIPPED_HEADERS.stream().noneMatch(skipped -> skipped.equalsIgnoreCase(name))) {
                headers.put(name, Collections.list(request.getHeaders(name)));
            }
        });
        return headers;
    }

    private static RestTemplate createRestTemplate() {
        RestTemplate template = new RestTemplate();
        template.setErrorHandler(new NoOpResponseErrorHandler());
        return template;
    }
}
