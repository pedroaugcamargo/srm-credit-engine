package com.srmasset.creditengine.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.srmasset.creditengine.application.CedenteNotFoundException;
import com.srmasset.creditengine.application.IdempotencyKeyRequiredException;
import com.srmasset.creditengine.application.IdempotencyKeyReusedException;
import com.srmasset.creditengine.application.ReceivableAlreadySettledException;
import com.srmasset.creditengine.application.ReceivableNotFoundException;
import com.srmasset.creditengine.application.SettlementNotFoundException;
import com.srmasset.creditengine.application.StatementQueryException;
import com.srmasset.creditengine.application.StatementQueryTimeoutException;
import com.srmasset.creditengine.domain.fx.ExternalFxClientException;
import com.srmasset.creditengine.domain.fx.ExternalFxServerException;
import com.srmasset.creditengine.domain.fx.ExternalFxTimeoutException;
import com.srmasset.creditengine.domain.fx.FxRateStaleException;
import com.srmasset.creditengine.domain.fx.FxRateUnavailableException;
import com.srmasset.creditengine.domain.pricing.InvalidTermException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(InvalidTermException.class)
	ProblemDetail invalidTerm(InvalidTermException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT,
				"O vencimento precisa ser posterior à data da operação.");
		problem.setProperty("code", "INVALID_TERM");
		return problem;
	}

	@ExceptionHandler(ReceivableNotFoundException.class)
	ProblemDetail receivableNotFound(ReceivableNotFoundException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
		problem.setProperty("code", "RECEIVABLE_NOT_FOUND");
		return problem;
	}

	@ExceptionHandler(SettlementNotFoundException.class)
	ProblemDetail settlementNotFound(SettlementNotFoundException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
		problem.setProperty("code", "SETTLEMENT_NOT_FOUND");
		return problem;
	}

	@ExceptionHandler(CedenteNotFoundException.class)
	ProblemDetail cedenteNotFound(CedenteNotFoundException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
		problem.setProperty("code", "CEDENTE_NOT_FOUND");
		return problem;
	}

	@ExceptionHandler(ReceivableAlreadySettledException.class)
	ProblemDetail receivableAlreadySettled(ReceivableAlreadySettledException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
		problem.setProperty("code", "RECEIVABLE_ALREADY_SETTLED");
		return problem;
	}

	@ExceptionHandler(IdempotencyKeyReusedException.class)
	ProblemDetail idempotencyKeyReused(IdempotencyKeyReusedException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT,
				exception.getMessage());
		problem.setProperty("code", "IDEMPOTENCY_KEY_REUSED");
		return problem;
	}

	@ExceptionHandler(StatementQueryException.class)
	ProblemDetail statementQuery(StatementQueryException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
		problem.setProperty("code", "STATEMENT_QUERY_INVALID");
		return problem;
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	ProblemDetail missingParameter(MissingServletRequestParameterException exception) {
		boolean period = "from".equals(exception.getParameterName()) || "to".equals(exception.getParameterName());
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				period ? "Informe o período." : "A requisição contém dados inválidos.");
		problem.setProperty("code", period ? "STATEMENT_QUERY_INVALID" : "VALIDATION_ERROR");
		return problem;
	}

	@ExceptionHandler(IdempotencyKeyRequiredException.class)
	ProblemDetail idempotencyKeyRequired(IdempotencyKeyRequiredException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
		problem.setProperty("code", "IDEMPOTENCY_KEY_REQUIRED");
		return problem;
	}

	@ExceptionHandler(FxRateStaleException.class)
	ProblemDetail fxRateStale(FxRateStaleException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT,
				exception.getMessage());
		problem.setProperty("code", "FX_RATE_STALE");
		return problem;
	}

	@ExceptionHandler(FxRateUnavailableException.class)
	ProblemDetail fxRateUnavailable(FxRateUnavailableException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT,
				exception.getMessage());
		problem.setProperty("code", "FX_RATE_UNAVAILABLE");
		return problem;
	}

	@ExceptionHandler({ExternalFxServerException.class, ExternalFxTimeoutException.class,
			ExternalFxClientException.class, CallNotPermittedException.class})
	ProblemDetail fxProviderUnavailable(Exception exception) {
		String detail = exception instanceof CallNotPermittedException
				? "Provedor de câmbio indisponível."
				: exception.getMessage();
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, detail);
		problem.setProperty("code", "FX_PROVIDER_UNAVAILABLE");
		return problem;
	}

	@ExceptionHandler(StatementQueryTimeoutException.class)
	ProblemDetail statementTimeout(StatementQueryTimeoutException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
				exception.getMessage());
		problem.setProperty("code", "STATEMENT_QUERY_TIMEOUT");
		return problem;
	}

	@ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
			MethodArgumentTypeMismatchException.class})
	ProblemDetail validation(Exception exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"A requisição contém dados inválidos.");
		problem.setProperty("code", "VALIDATION_ERROR");
		return problem;
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> unexpected(Exception exception) {
		// Erros do próprio Spring MVC (rota inexistente, verbo errado) já sabem o
		// status certo.
		if (exception instanceof ErrorResponse response) {
			return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders())
					.body(response.getBody());
		}
		log.error("Erro inesperado", exception);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
				"Erro inesperado. Tente novamente mais tarde.");
		problem.setProperty("code", "INTERNAL_ERROR");
		return ResponseEntity.internalServerError().body(problem);
	}

}
