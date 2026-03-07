package com.eolma.auction.infra;

import com.eolma.common.exception.EolmaException;
import com.eolma.common.exception.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice
public class AuctionExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AuctionExceptionHandler.class);

    @ExceptionHandler(EolmaException.class)
    public ProblemDetail handleEolmaException(EolmaException e) {
        ErrorType errorType = e.getErrorType();
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.valueOf(errorType.getStatus()),
                e.getDetail()
        );
        problemDetail.setType(URI.create(errorType.getType()));
        problemDetail.setTitle(errorType.getTitle());

        if (errorType.getStatus() >= 500) {
            log.error("Server error: type={}, detail={}", errorType.getType(), e.getDetail(), e);
        } else {
            log.warn("Client error: type={}, detail={}", errorType.getType(), e.getDetail());
        }

        return problemDetail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleException(Exception e) {
        log.error("Unexpected error", e);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "예기치 않은 오류가 발생했습니다."
        );
        problemDetail.setType(URI.create(ErrorType.INTERNAL_ERROR.getType()));
        problemDetail.setTitle(ErrorType.INTERNAL_ERROR.getTitle());
        return problemDetail;
    }
}
