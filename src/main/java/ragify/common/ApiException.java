package ragify.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String detail) {
        super(detail);
        this.status = status;
    }

    public ApiException(int status, String detail) {
        this(HttpStatus.valueOf(status), detail);
    }
}
