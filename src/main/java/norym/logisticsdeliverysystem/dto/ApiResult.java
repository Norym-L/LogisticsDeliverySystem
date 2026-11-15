package norym.logisticsdeliverysystem.dto;

/**
 * 统一响应结构。
 * code = 0 表示成功，非 0 为业务错误码。
 */
public record ApiResult<T>(int code, String message, T data)
{
    public static <T> ApiResult<T> ok(T data)
    {
        return new ApiResult<>(0, "ok", data);
    }

    public static <T> ApiResult<T> ok()
    {
        return new ApiResult<>(0, "ok", null);
    }

    public static <T> ApiResult<T> fail(int code, String message)
    {
        return new ApiResult<>(code, message, null);
    }
}
