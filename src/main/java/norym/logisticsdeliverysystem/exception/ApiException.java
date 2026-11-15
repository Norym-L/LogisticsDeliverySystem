package norym.logisticsdeliverysystem.exception;

/**
 * 业务异常。code 与 HTTP 状态码保持一致，前端只认 code 也能处理。
 */
public class ApiException extends RuntimeException
{
    private final int code;

    public ApiException(int code, String message)
    {
        super(message);
        this.code = code;
    }

    public int getCode()
    {
        return code;
    }
}
