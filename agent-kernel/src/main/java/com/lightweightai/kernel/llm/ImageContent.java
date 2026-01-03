package com.lightweightai.kernel.llm;

/**
 * Image content block - for multimodal support
 */
public class ImageContent implements ContentBlock {

    private final String url;
    private final byte[] data;
    private final String mimeType;

    public ImageContent(String url, String mimeType) {
        this.url = url;
        this.data = null;
        this.mimeType = mimeType;
    }

    public ImageContent(byte[] data, String mimeType) {
        this.url = null;
        this.data = data;
        this.mimeType = mimeType;
    }

    @Override
    public ContentType getType() {
        return ContentType.IMAGE;
    }

    public String getUrl() {
        return url;
    }

    public byte[] getData() {
        return data;
    }

    public String getMimeType() {
        return mimeType;
    }

    public boolean isUrl() {
        return url != null;
    }
}
