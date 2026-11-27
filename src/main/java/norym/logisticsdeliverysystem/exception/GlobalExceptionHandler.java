package norym.logisticsdeliverysystem.exception;

import norym.logisticsdeliverysystem.dto.ApiResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler
{
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResult<Void>> handleApi(ApiException e)
    {
        return ResponseEntity.status(toHttpStatus(e.getCode()))
                .body(ApiResult.fail(e.getCode(), e.getMessage()));
    }

    /**
     * 静态资源不存在时 Spring 抛的是 NoResourceFoundException，
     * 不单独处理会落到下面的兜底分支，变成一个 500——那是错的，应该是 404。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResult<Void>> handleNotFound(NoResourceFoundException e)
    {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResult.fail(404, "资源不存在：" + e.getResourcePath()));
    }

    /** 长度或约束没在业务层拦住时，仍然返回 400，而不是笼统的 500 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResult<Void>> handleData(DataIntegrityViolationException e)
    {
        log.warn("数据约束未通过", e);
        return ResponseEntity.badRequest()
                .body(ApiResult.fail(400, "提交的内容超出长度或不符合数据约束"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleOther(Exception e)
    {
        log.error("未处理的异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResult.fail(500, "服务器内部错误"));
    }

    private HttpStatus toHttpStatus(int code)
    {
        HttpStatus status = HttpStatus.resolve(code);
        return status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
    }
}
