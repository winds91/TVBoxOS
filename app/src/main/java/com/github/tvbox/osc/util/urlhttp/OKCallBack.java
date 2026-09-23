package com.github.tvbox.osc.util.urlhttp;

import okhttp3.Call;
import okhttp3.Response;


public abstract class OKCallBack<T> {

    private T result = null;

    public T getResult() {
        return result;
    }

    protected void setResult(T val) {
        result = val;
    }

    protected void onError(final Call call, final Exception e) {
        onFailure(call, e);
    }

    protected void onSuccess(Call call, Response response) {
        T obj = onParseResponse(call, response);
        setResult(obj);
        onResponse(obj);
    }

    protected abstract T onParseResponse(Call call, Response response);

    protected abstract void onFailure(Call call, Exception e);

    protected abstract void onResponse(T response);
}
