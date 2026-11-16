package norym.logisticsdeliverysystem.dto;

/**
 * 二维码内容。
 * <p>
 * 服务器只负责给出"编进二维码的字符串"，不生成图片：
 * 二维码本质是文本，出图是展示层职责，后端出图会引入 QR 编码库
 * （Reed-Solomon 纠错不是能随手手写的东西），且图片无法做缓存与尺寸适配。
 * 前端用 qrcode.js 或小程序 canvas 把这个 content 画出来即可。
 *
 * @param orderNo 邮件编号
 * @param content 二维码内容，扫码后原样得到该字符串
 * @param type    内容类型，便于前端判断如何解析
 */
public record QrCodeView(String orderNo, String content, String type)
{
}
