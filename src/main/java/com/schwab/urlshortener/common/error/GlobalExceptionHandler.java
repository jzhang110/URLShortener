package com.schwab.urlshortener.common.error;

import com.schwab.urlshortener.common.filter.CorrelationIdFilter;
import com.schwab.urlshortener.common.logging.LogSanitizer;
import com.schwab.urlshortener.url.domain.InvalidUrlException;
import com.schwab.urlshortener.url.domain.ShortCodeExhaustedException;
import com.schwab.urlshortener.url.domain.ShortCodeNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps every error to an RFC 9457 problem response with a stable {@code code}, the request's
 * {@code correlationId} and a {@code timestamp}. Messages are fixed, client-safe strings; exception
 * details stay in server-side logs only.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    // Stable, machine-readable problem types (RFC 9457 §3.1.1); not dereferenceable URLs.
    private static final String PROBLEM_TYPE_PREFIX = "urn:problem-type:url-shortener:";
    static final String GENERIC_ERROR_DETAIL = "An unexpected error occurred. Quote the correlation id when reporting it.";

    @ExceptionHandler(InvalidUrlException.class)
    ProblemDetail handleInvalidUrl(InvalidUrlException e, HttpServletRequest request) {
        log.debug("Rejected URL: {}", e.reason());
        return problem(HttpStatus.BAD_REQUEST, e.reason().name(), e.reason().message(), request);
    }

    @ExceptionHandler(ShortCodeNotFoundException.class)
    ProblemDetail handleNotFound(ShortCodeNotFoundException e, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "SHORT_CODE_NOT_FOUND", "No URL exists for this short code.", request);
    }

    @ExceptionHandler(ShortCodeExhaustedException.class)
    ProblemDetail handleExhausted(ShortCodeExhaustedException e, HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "SHORT_CODE_UNAVAILABLE",
                "A short code could not be allocated. Please try again later.", request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("Unhandled error processing {} {}", request.getMethod(),
                LogSanitizer.sanitize(request.getRequestURI()), e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", GENERIC_ERROR_DETAIL, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = e.getBody();
        body.setDetail("The request is invalid.");
        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", String.valueOf(error.getDefaultMessage())))
                .toList();
        body.setProperty("errors", errors);
        return handleExceptionInternal(e, body, headers, status, request);
    }

    /** Enriches problem bodies produced by Spring MVC's built-in handlers (malformed JSON, 405, 415, ...). */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        if (body instanceof ProblemDetail problem && request instanceof ServletWebRequest servletRequest) {
            enrich(problem, codeFor(status), servletRequest.getRequest());
        }
        return super.handleExceptionInternal(e, body, headers, status, request);
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        enrich(problem, code, request);
        return problem;
    }

    private static void enrich(ProblemDetail problem, String code, HttpServletRequest request) {
        problem.setType(URI.create(PROBLEM_TYPE_PREFIX + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        problem.setProperty("timestamp", Instant.now().toString());
    }

    private static String codeFor(HttpStatusCode status) {
        HttpStatus known = HttpStatus.resolve(status.value());
        return known != null ? known.name() : "HTTP_" + status.value();
    }
}
