package cn.geelato.web.platform.filter;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class CustomHttpServletResponse extends HttpServletResponseWrapper {
    private final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    public CustomHttpServletResponse(HttpServletResponse response) {
        super(response);
    }
    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        return new CustomServletOutputStream();
    }
    private class CustomServletOutputStream extends ServletOutputStream {
        @Override
        public void write(int b) throws IOException {
            byteArrayOutputStream.write(b);
        }
        @Override
        public void write(byte[] arg0) throws IOException {
            byteArrayOutputStream.write(arg0);
        }
        @Override
        public boolean isReady() {
            return false;
        }
        @Override
        public void setWriteListener(WriteListener writeListener) {
        }
    }
    public String  getData() throws UnsupportedEncodingException {
        return byteArrayOutputStream.toString(StandardCharsets.UTF_8);
    }
}
