package com.cdkj.ylq.callback;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.cdkj.ylq.ao.IBorrowAO;
import com.cdkj.ylq.ao.ICertificationAO;
import com.cdkj.ylq.ao.IJdtAO;
import com.cdkj.ylq.common.JsonUtil;
import com.cdkj.ylq.domain.MxCarrierNofification;
import com.cdkj.ylq.enums.EBizType;
import com.cdkj.ylq.enums.EChannelType;
import com.cdkj.ylq.enums.EPayType;

/** 
 * @author: haiqingzheng 
 * @since: 2016年12月26日 下午1:44:16 
 * @history:
 */
@Controller
public class CallbackConroller {

    private static Logger logger = Logger.getLogger(CallbackConroller.class);

    private static final String HEADER_MOXIE_EVENT = "X-Moxie-Event";

    private static final String HEADER_MOXIE_TYPE = "X-Moxie-Type";

    private static final String HEADER_MOXIE_SIGNATURE = "X-Moxie-Signature";

    @Autowired
    IBorrowAO borrowAO;

    @Autowired
    ICertificationAO certificationAO;

    @Autowired
    IJdtAO jdtAO;

    // ******第三方支付成功回调处理

    @RequestMapping("/thirdPay/callback")
    public synchronized void doCallbackYLQ(HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        boolean isSuccess = Boolean.valueOf(request.getParameter("isSuccess"));
        String channelType = request.getParameter("channelType");
        String payGroup = request.getParameter("payGroup");
        String payCode = request.getParameter("payCode");
        String amountString = request.getParameter("transAmount");
        Long amount = 0L;
        if (StringUtils.isNotBlank(amountString)) {
            amount = Long.valueOf(amountString);
        }
        String bizType = request.getParameter("bizType");
        String payType = getPayType(channelType);
        String transNo = request.getParameter("transNo");
        String transRemark = request.getParameter("transRemark");
        // 支付成功，商户处理后同步返回给微信参数
        if (!isSuccess) {
            if (EBizType.YLQ_BAOFOO_PAY_QUERY.getCode().equals(bizType)) {
                borrowAO.doLoanBaofooQueryCallback(transNo, false, transRemark);
            }
            logger.info("****业务类型<" + bizType + "> payGroup <" + payGroup
                    + "> payCode <" + payCode + ">回调失败****");
        } else {
            try {
                if (EBizType.YLQ_REPAY.getCode().equals(bizType)) {
                    borrowAO.repaySuccess(payGroup, payType, payCode, amount);
                } else if (EBizType.YLQ_RENEWAL.getCode().equals(bizType)) {
                    borrowAO.renewalSuccess(payGroup, payType, payCode, amount);
                } else if (EBizType.YLQ_BAOFOO_PAY_QUERY.getCode().equals(
                    bizType)) {
                    borrowAO.doLoanBaofooQueryCallback(transNo, true,
                        transRemark);
                }

            } catch (Exception e) {
                logger.error("支付回调异常payGroup <" + payGroup + "> payCode <"
                        + payCode + ">异常如下：" + e.getMessage());
            }
        }
    }

    // ******魔蝎报告回调处理

    @RequestMapping("/mxcarrier/notifications")
    public synchronized void doCallbackMxCarrier(@RequestBody String body,
            ServletRequest request, ServletResponse response)
            throws IOException {

        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        HttpServletResponse httpServletResponse = (HttpServletResponse) response;

        // 事件类型：task or bill
        String eventName = httpServletRequest.getHeader(HEADER_MOXIE_EVENT);

        // 业务类型：email、bank、carrier 等
        String eventType = httpServletRequest.getHeader(HEADER_MOXIE_TYPE);

        // body签名
        String signature = httpServletRequest.getHeader(HEADER_MOXIE_SIGNATURE);

        if (StringUtils.isBlank(eventName)) {
            writeMessage(httpServletResponse,
                HttpServletResponse.SC_BAD_REQUEST, "header not found:"
                        + HEADER_MOXIE_EVENT);
            return;
        }

        if (StringUtils.isBlank(eventType)) {
            writeMessage(httpServletResponse,
                HttpServletResponse.SC_BAD_REQUEST, "header not found:"
                        + HEADER_MOXIE_TYPE);
            return;
        }

        if (StringUtils.isBlank(signature)) {
            writeMessage(httpServletResponse,
                HttpServletResponse.SC_BAD_REQUEST, "header not found:"
                        + HEADER_MOXIE_SIGNATURE);
            return;
        }
        if (StringUtils.isBlank(body)) {
            writeMessage(httpServletResponse,
                HttpServletResponse.SC_BAD_REQUEST, "request body is empty");
            return;
        }

        // 任务提交
        // if (StringUtils.equals(eventName.toLowerCase(), "task.submit")) {
        // // 通知状态变更为 '认证中'
        // MxCarrierNofification notification = JsonUtil.json2Bean(body,
        // MxCarrierNofification.class);
        // certificationAO.doMxCarrierTaskSubmitCallback(notification);
        // }

        // 登录完成后的通知，包括登录成功或者失败
        if (StringUtils.equals(eventName.toLowerCase(), "task")) {
            MxCarrierNofification notification = JsonUtil.json2Bean(body,
                MxCarrierNofification.class);
            certificationAO.doMxCarrierTaskCallback(notification);
        }

        // 任务采集失败
        if (StringUtils.equals(eventName.toLowerCase(), "task.fail")) {
            // 通知状态变更为 '认证失败'
            MxCarrierNofification notification = JsonUtil.json2Bean(body,
                MxCarrierNofification.class);
            certificationAO.doMxCarrierTaskFailCallback(notification);
        }

        // 如果事件类型是report(用户报告通知)
        if (StringUtils.equals(eventName.toLowerCase(), "report")) {
            try {
                MxCarrierNofification notification = JsonUtil.json2Bean(body,
                    MxCarrierNofification.class);
                certificationAO.doMxCarrierReportCallback(notification);
            } catch (Exception e) {
                logger.error("body convert to object error", e);
            }
        }

        writeMessage(httpServletResponse, HttpServletResponse.SC_CREATED,
            "default eventtype");
    }

    /* 借贷通回调处理 */
    @RequestMapping("/jdt/notice")
    public synchronized void doCallbackJDT(HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        try {
            // 获取回调参数
            PrintWriter out = response.getWriter();
            String token = request.getParameter("token");
            String uid = request.getParameter("uid");
            String demandKey = request.getParameter("demandKey");
            String demandId = request.getParameter("demandId");
            String dataTime = request.getParameter("dataTime");
            logger.info("借贷通获客-实时通告" + "token=" + token + " uid=" + uid
                    + " demandKey=" + demandKey + " demandId=" + demandId
                    + " dataTime=" + dataTime);
            jdtAO.doGetNewMember(token, uid, demandKey, demandId, dataTime);
            out.print("received");
        } catch (Exception e) {
            logger.info("借贷通实时通告处理异常,原因：" + e.getMessage());
        }
    }

    private String getPayType(String channelType) {
        String payType = null;
        if (EChannelType.Alipay.getCode().equals(channelType)) {
            payType = EPayType.ALIPAY.getCode();
        } else if (EChannelType.WeChat_APP.getCode().equals(channelType)) {
            payType = EPayType.WEIXIN_APP.getCode();
        }
        return payType;
    }

    private void writeMessage(HttpServletResponse response, int status,
            String content) {
        response.setStatus(status);
        try {
            PrintWriter printWriter = response.getWriter();
            printWriter.write(content);
        } catch (IOException ignored) {
        }
    }
}
