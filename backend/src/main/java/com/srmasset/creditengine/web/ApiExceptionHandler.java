package com.srmasset.creditengine.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.srmasset.creditengine.domain.fx.FxRateUnavailableException;
import com.srmasset.creditengine.domain.pricing.InvalidTermException;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(InvalidTermException.class)
	ProblemDetail invalidTerm(InvalidTermException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.UNPROCESSABLE_CONTENT,
				"O vencimento precisa ser posterior à data da operação.");
		problem.setProperty("code", "INVALID_TERM");
		return problem;
	}

	@ExceptionHandler(FxRateUnavailableException.class)
	ProblemDetail fxRateUnavailable(FxRateUnavailableException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.UNPROCESSABLE_CONTENT,
				exception.getMessage());
		problem.setProperty("code", "FX_RATE_UNAVAILABLE");
		return problem;
	}

	@ExceptionHandler({ MethodArgumentNotValidException.class, HttpMessageNotReadableException.class })
	ProblemDetail validation(Exception exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.BAD_REQUEST,
				"A requisição contém dados inválidos.");
		problem.setProperty("code", "VALIDATION_ERROR");
		return problem;
	}

}
