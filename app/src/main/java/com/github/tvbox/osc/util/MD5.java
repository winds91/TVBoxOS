package com.github.tvbox.osc.util;

import android.text.TextUtils;
import android.util.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


/**
 * <b>类名称：</b> MD5 <br/>
 * <b>类描述：</b> MD5值计算<br/>
 * <b>创建人：</b> 林肯 <br/>
 * <b>修改人：</b> 编辑人 <br/>
 * <b>修改时间：</b> 2015年08月11日 下午2:41 <br/>
 * <b>修改备注：</b> <br/>
 *
 * @version 1.0.0 <br/>
 */
public class MD5 {
    /**
     * 消息摘要.
     */
    private static MessageDigest sDigest;

    static {
        try {
            MD5.sDigest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            Log.e("获取MD5信息摘要失败", e.getMessage());
        }
    }

    /**
     * MD5加码 生成32位md5码
     */
    public static String string2MD5(String inStr) {
        if (sDigest == null) {
            Log.e("MD5", "MD5信息摘要初始化失败");
            return null;
        } else if (TextUtils.isEmpty(inStr)) {
            Log.e("MD5", "参数strSource不能为空");
            return null;
        }
        char[] charArray = inStr.toCharArray();
        byte[] byteArray = new byte[charArray.length];

        for (int i = 0; i < charArray.length; i++)
            byteArray[i] = (byte) charArray[i];
        byte[] md5Bytes = sDigest.digest(byteArray);
        StringBuilder hexValue = new StringBuilder();
        for (byte md5Byte : md5Bytes) {
            int val = ((int) md5Byte) & 0xff;
            if (val < 16)
                hexValue.append("0");
            hexValue.append(Integer.toHexString(val));
        }
        return hexValue.toString();

    }
}
