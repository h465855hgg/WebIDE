package com.web.webide.html;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简单的HTML词法分析器
 * 用于将HTML字符串分解为标记(token)
 */
public class HtmlLexer {

    /**
     * HTML标记类型枚举
     */
    public enum TokenType {
        TEXT,           // 文本内容
        TAG_OPEN,       // 开始标签
        TAG_CLOSE,      // 结束标签
        TAG_SELF_CLOSING, // 自闭合标签
        COMMENT,        // 注释
        DOCTYPE,        // 文档类型声明
        PROCESSING_INSTRUCTION // 处理指令
    }

    /**
     * 标记类，表示HTML中的一个语法单元
     */
    public static class Token {
        private final TokenType type;
        private final String value;
        private final int position;
        private final int length;

        public Token(TokenType type, String value, int position, int length) {
            this.type = type;
            this.value = value;
            this.position = position;
            this.length = length;
        }

        public TokenType getType() {
            return type;
        }

        public String getValue() {
            return value;
        }

        public int getPosition() {
            return position;
        }

        public int getLength() {
            return length;
        }

        @Override
        public String toString() {
            return String.format("Token(%s, \"%s\", %d, %d)", type, value, position, length);
        }
    }

    // 常见的自闭合标签列表（包括HTML5标准中所有的自闭合标签）
    private static final String[] SELF_CLOSING_TAGS = {
            "area", "base", "br", "col", "embed", "hr", "img", "input",
            "link", "meta", "param", "source", "track", "wbr", "command",
            "keygen", "menuitem", "rp", "rt", "rtc", "data"
    };

    // 预编译正则表达式以提高性能
    private static final Pattern HTML_TAG_PATTERN =
            Pattern.compile("<\\s*([^>/\\s]+)([^>]*)>", Pattern.CASE_INSENSITIVE);

    private static final Pattern HTML_COMMENT_PATTERN =
            Pattern.compile("<!--.*?-->", Pattern.DOTALL);

    private static final Pattern DOCTYPE_PATTERN =
            Pattern.compile("<!DOCTYPE\\s+[^>]+>", Pattern.CASE_INSENSITIVE);

    private static final Pattern PROCESSING_INSTRUCTION_PATTERN =
            Pattern.compile("<\\?.*?\\?>", Pattern.DOTALL);

    private static final Pattern ATTRIBUTE_PATTERN =
            Pattern.compile("\\s*(\\w+)\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)");

    /**
     * 对HTML内容进行词法分析
     * @param htmlContent HTML源码
     * @return 标记列表
     */
    public static List<Token> tokenize(String htmlContent) {
        List<Token> tokens = new ArrayList<>();
        if (htmlContent == null || htmlContent.isEmpty()) {
            return tokens;
        }

        int pos = 0;
        while (pos < htmlContent.length()) {
            char ch = htmlContent.charAt(pos);

            if (ch == '<') {
                // 查找可能的结束括号位置
                int tagEnd = htmlContent.indexOf('>', pos);
                if (tagEnd == -1) {
                    // 没有找到结束括号，把剩余内容作为文本处理
                    tokens.add(new Token(TokenType.TEXT, htmlContent.substring(pos), pos,
                            htmlContent.length() - pos));
                    break;
                }

                String tagContent = htmlContent.substring(pos + 1, tagEnd);

                // 判断标记类型
                if (tagContent.startsWith("!")) {
                    // 注释检查
                    if (tagContent.startsWith("!--")) {
                        tokens.add(new Token(TokenType.COMMENT, tagContent, pos,
                                tagEnd - pos + 1));
                    } else {
                        // 其他注释形式也当作文本处理
                        tokens.add(new Token(TokenType.TEXT, htmlContent.substring(pos, tagEnd + 1),
                                pos, tagEnd - pos + 1));
                    }
                } else if (tagContent.startsWith("?")) {
                    // 处理指令
                    tokens.add(new Token(TokenType.PROCESSING_INSTRUCTION, tagContent, pos,
                            tagEnd - pos + 1));
                } else if (tagContent.equalsIgnoreCase("![CDATA[")) {
                    // CDATA区域
                    int cdataEnd = htmlContent.indexOf("]]>", pos);
                    if (cdataEnd != -1) {
                        tokens.add(new Token(TokenType.TEXT,
                                htmlContent.substring(pos, cdataEnd + 3),
                                pos, cdataEnd + 3 - pos));
                        pos = cdataEnd + 3;
                        continue;
                    } else {
                        tokens.add(new Token(TokenType.TEXT, htmlContent.substring(pos),
                                pos, htmlContent.length() - pos));
                        break;
                    }
                } else if (tagContent.matches("^!doctype\\s+.+$")) {
                    // 文档类型声明
                    tokens.add(new Token(TokenType.DOCTYPE, tagContent, pos,
                            tagEnd - pos + 1));
                } else if (tagContent.startsWith("/")) {
                    // 结束标签
                    tokens.add(new Token(TokenType.TAG_CLOSE, tagContent, pos,
                            tagEnd - pos + 1));
                } else {
                    // 开始标签或自闭合标签
                    boolean isSelfClosing = tagContent.endsWith("/") ||
                            isSelfClosingTag(tagContent);

                    if (isSelfClosing) {
                        tokens.add(new Token(TokenType.TAG_SELF_CLOSING, tagContent, pos,
                                tagEnd - pos + 1));
                    } else {
                        tokens.add(new Token(TokenType.TAG_OPEN, tagContent, pos,
                                tagEnd - pos + 1));
                    }
                }

                pos = tagEnd + 1;
            } else {
                // 处理普通文本
                int nextTag = htmlContent.indexOf('<', pos);
                String text;
                int textLength;

                if (nextTag == -1) {
                    // 没有更多标签了，整个剩余部分都是文本
                    text = htmlContent.substring(pos);
                    textLength = text.length();
                    pos = htmlContent.length();
                } else {
                    // 找到下一个标签位置
                    text = htmlContent.substring(pos, nextTag);
                    textLength = text.length();
                    pos = nextTag;
                }

                // 只添加非空文本
                if (!text.trim().isEmpty() || (text.length() > 0 && !Character.isWhitespace(text.charAt(0)))) {
                    tokens.add(new Token(TokenType.TEXT, text, pos - textLength, textLength));
                }
            }
        }

        return tokens;
    }

    /**
     * 判断是否为自闭合标签
     */
    private static boolean isSelfClosingTag(String tagContent) {
        // 提取标签名
        String tagName = extractTagName(tagContent);
        if (tagName == null) return false;

        // 转换为小写进行比较
        String lowerTagName = tagName.toLowerCase();

        // 在预定义的自闭合标签列表中查找
        for (String selfClosing : SELF_CLOSING_TAGS) {
            if (selfClosing.equalsIgnoreCase(lowerTagName)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 从标签内容中提取标签名
     */
    private static String extractTagName(String tagContent) {
        if (tagContent == null || tagContent.isEmpty()) {
            return null;
        }

        // 移除可能存在的属性部分
        int spaceIndex = tagContent.indexOf(' ');
        if (spaceIndex != -1) {
            return tagContent.substring(0, spaceIndex).trim();
        }

        // 如果没有空格，就返回整个内容（但要去掉开头的斜杠）
        if (tagContent.startsWith("/")) {
            return tagContent.substring(1).trim();
        }

        return tagContent.trim();
    }

    /**
     * 解析标签属性
     * @param tagContent 标签内容
     * @return 属性键值对列表
     */
    public static List<Attribute> parseAttributes(String tagContent) {
        List<Attribute> attributes = new ArrayList<>();
        if (tagContent == null || tagContent.isEmpty()) {
            return attributes;
        }

        // 移除标签名，保留属性部分
        String contentWithoutTag = tagContent;
        int firstSpaceIndex = tagContent.indexOf(' ');
        if (firstSpaceIndex != -1) {
            contentWithoutTag = tagContent.substring(firstSpaceIndex + 1).trim();
        }

        // 匹配所有属性
        Matcher matcher = ATTRIBUTE_PATTERN.matcher(contentWithoutTag);
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2);

            // 移除引号
            if ((value.startsWith("\"") && value.endsWith("\"")) ||
                    (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }

            attributes.add(new Attribute(key, value));
        }

        return attributes;
    }

    /**
     * 属性类
     */
    public static class Attribute {
        private final String name;
        private final String value;

        public Attribute(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return name + "=" + value;
        }
    }

    /**
     * 打印标记列表供调试
     */
    public static void printTokens(List<Token> tokens) {
        System.out.println("=== HTML Tokens ===");
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            System.out.printf("%d: %s%n", i, token);
        }
        System.out.println("===================");
    }

    /**
     * 获取HTML内容的基本统计信息
     */
    public static void analyzeHtmlStructure(String htmlContent) {
        System.out.println("=== HTML Structure Analysis ===");
        System.out.println("原始HTML长度: " + htmlContent.length());

        List<Token> tokens = tokenize(htmlContent);
        System.out.println("总标记数: " + tokens.size());

        int textCount = 0;
        int openTagCount = 0;
        int closeTagCount = 0;
        int selfClosingTagCount = 0;
        int commentCount = 0;
        int doctypeCount = 0;
        int processingInstructionCount = 0;

        for (Token token : tokens) {
            switch (token.getType()) {
                case TEXT:
                    textCount++;
                    break;
                case TAG_OPEN:
                    openTagCount++;
                    break;
                case TAG_CLOSE:
                    closeTagCount++;
                    break;
                case TAG_SELF_CLOSING:
                    selfClosingTagCount++;
                    break;
                case COMMENT:
                    commentCount++;
                    break;
                case DOCTYPE:
                    doctypeCount++;
                    break;
                case PROCESSING_INSTRUCTION:
                    processingInstructionCount++;
                    break;
            }
        }

        System.out.println("文本数量: " + textCount);
        System.out.println("开始标签数量: " + openTagCount);
        System.out.println("结束标签数量: " + closeTagCount);
        System.out.println("自闭合标签数量: " + selfClosingTagCount);
        System.out.println("注释数量: " + commentCount);
        System.out.println("文档类型声明数量: " + doctypeCount);
        System.out.println("处理指令数量: " + processingInstructionCount);
        System.out.println("===============================");
    }

    /**
     * 简化版的HTML结构解析方法
     */
    public static void simpleParse(String htmlContent) {
        System.out.println("=== Simple Parse Result ===");
        List<Token> tokens = tokenize(htmlContent);

        for (Token token : tokens) {
            switch (token.getType()) {
                case TEXT:
                    System.out.printf("[TEXT] '%s'%n", token.getValue().trim());
                    break;
                case TAG_OPEN:
                    System.out.printf("[OPEN_TAG] <%s>%n", token.getValue());
                    break;
                case TAG_CLOSE:
                    System.out.printf("[CLOSE_TAG] </%s>%n", token.getValue().substring(1));
                    break;
                case TAG_SELF_CLOSING:
                    System.out.printf("[SELF_CLOSING_TAG] <%s/>%n", token.getValue());
                    break;
                case COMMENT:
                    System.out.printf("[COMMENT] <!--%s-->%n", token.getValue());
                    break;
                case DOCTYPE:
                    System.out.printf("[DOCTYPE] <!DOCTYPE %s>%n", token.getValue());
                    break;
                case PROCESSING_INSTRUCTION:
                    System.out.printf("[PROCESSING_INSTRUCTION] <?%s?>%n", token.getValue());
                    break;
            }
        }
        System.out.println("===========================");
    }
}
