package com.web.webide.html;

import java.util.*;

/**
 * 基于词法分析的HTML标签闭合检查器
 * 使用StandardHtmlLexer进行词法分析并检测未闭合标签
 */
public class HtmlTagClosureChecker {

    /**
     * 标签信息类
     */
    public static class TagInfo {
        private final String tagName;
        private final int lineNumber;
        private final int position;
        private final boolean isSelfClosing;

        public TagInfo(String tagName, int lineNumber, int position, boolean isSelfClosing) {
            this.tagName = tagName;
            this.lineNumber = lineNumber;
            this.position = position;
            this.isSelfClosing = isSelfClosing;
        }

        public String getTagName() {
            return tagName;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public int getPosition() {
            return position;
        }

        public boolean isSelfClosing() {
            return isSelfClosing;
        }

        @Override
        public String toString() {
            return String.format("TagInfo{tag='%s', line=%d, pos=%d, selfClosing=%s}",
                    tagName, lineNumber, position, isSelfClosing);
        }
    }

    /**
     * 验证结果类
     */
    public static class ValidationResult {
        private final boolean isValid;
        private final List<TagInfo> unclosedTags;
        private final List<String> errors;

        public ValidationResult(boolean isValid, List<TagInfo> unclosedTags, List<String> errors) {
            this.isValid = isValid;
            this.unclosedTags = unclosedTags;
            this.errors = errors;
        }

        public boolean isValid() {
            return isValid;
        }

        public List<TagInfo> getUnclosedTags() {
            return unclosedTags;
        }

        public List<String> getErrors() {
            return errors;
        }

        @Override
        public String toString() {
            return String.format("ValidationResult{valid=%s, unclosed=%d tags, errors=%d}",
                    isValid, unclosedTags.size(), errors.size());
        }
    }

    // 自闭合标签列表
    private static final Set<String> SELF_CLOSING_TAGS = new HashSet<>(Arrays.asList(
            "area", "base", "br", "col", "embed", "hr", "img", "input",
            "link", "meta", "param", "source", "track", "wbr", "command",
            "keygen", "menuitem", "rp", "rt", "rtc", "data", "datalist",
            "output", "progress", "meter", "canvas", "audio", "video",
            "object", "script", "style", "textarea", "select",
            "option", "optgroup", "fieldset", "legend", "form", "button",
            "label", "input", "textarea", "select", "optgroup", "option"
    ));

    /**
     * 分析HTML标签闭合情况 - 严格按照指定逻辑实现
     * @param htmlContent HTML源码
     * @return 验证结果
     */
    public static ValidationResult analyzeTagClosure(String htmlContent) {
        List<TagInfo> unclosedTags = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Stack<TagInfo> openTagsStack = new Stack<>(); // 使用栈来跟踪未闭合的标签

        if (htmlContent == null || htmlContent.isEmpty()) {
            return new ValidationResult(true, unclosedTags, errors);
        }

        // 使用标准词法分析器进行分词
        List<HtmlLexer.Token> tokens = HtmlLexer.tokenize(htmlContent);

        for (HtmlLexer.Token token : tokens) {
            switch (token.getType()) {
                case TAG_OPEN:
                    // 遇到开始标签时将标签记录到栈中
                    handleOpenTag(token, openTagsStack, errors, htmlContent);
                    break;

                case TAG_CLOSE:
                    // 遇到结束标签时从栈中删除对应的开始标签
                    handleCloseTag(token, openTagsStack, errors, htmlContent);
                    break;

                case TAG_SELF_CLOSING:
                    // 遇到自闭合标签时直接跳过
                    handleSelfClosingTag(token, errors, htmlContent);
                    break;

                default:
                    // 其他类型标记不处理
                    break;
            }
        }

        // 检查是否有未闭合的开始标签
        unclosedTags.addAll(openTagsStack);
        for (TagInfo tag : openTagsStack) {
            errors.add(String.format("行 %d: 标签 <%s> 未闭合",
                    tag.getLineNumber(), tag.getTagName()));
        }

        boolean isValid = unclosedTags.isEmpty() && errors.isEmpty();
        return new ValidationResult(isValid, unclosedTags, errors);
    }

    /**
     * 处理开始标签
     */
    private static void handleOpenTag(HtmlLexer.Token token,
                                      Stack<TagInfo> openTagsStack,
                                      List<String> errors,
                                      String htmlContent) {
        String tagName = extractTagName(token.getValue());
        boolean isSelfClosing = false; // 开始标签不会是自闭合的

        int lineNumber = calculateLineFromPosition(htmlContent, token.getPosition());

        TagInfo tagInfo = new TagInfo(tagName.toLowerCase(), lineNumber,
                token.getPosition(), isSelfClosing);

        openTagsStack.push(tagInfo); // 将开始标签推入栈
    }

    /**
     * 处理结束标签
     */
    private static void handleCloseTag(HtmlLexer.Token token,
                                       Stack<TagInfo> openTagsStack,
                                       List<String> errors,
                                       String htmlContent) {
        String tagName = extractTagName(token.getValue());
        int lineNumber = calculateLineFromPosition(htmlContent, token.getPosition());

        // 如果栈为空，说明存在未匹配的结束标签
        if (openTagsStack.isEmpty()) {
            errors.add(String.format("行 %d: 发现未匹配的结束标签 </%s>",
                    lineNumber, tagName));
            return;
        }

        // 获取栈顶元素（最近打开的标签）
        TagInfo openedTag = openTagsStack.peek();

        // 检查是否完全匹配
        if (!openedTag.getTagName().equals(tagName.toLowerCase())) {
            // 标签不匹配的情况 - 这就是您报告的错误原因
            errors.add(String.format("行 %d: 标签不匹配 - 开始标签 <%s> 与结束标签 </%s> 不匹配",
                    lineNumber, openedTag.getTagName(), tagName));
            // 注意：这里不弹出标签，让问题继续暴露出来
        } else {
            // 完全匹配的情况下才弹出标签
            openTagsStack.pop();
        }
    }

    /**
     * 处理自闭合标签
     */
    private static void handleSelfClosingTag(HtmlLexer.Token token,
                                             List<String> errors,
                                             String htmlContent) {
        // 自闭合标签直接跳过，不需要做任何处理
        // 这里的逻辑只是简单跳过，不影响其他标签的匹配过程
    }

    /**
     * 从标签内容中提取标签名
     */
    private static String extractTagName(String tagContent) {
        if (tagContent == null || tagContent.isEmpty()) {
            return "";
        }

        // 移除可能存在的斜杠（如果是结束标签）
        String cleanContent = tagContent.trim();
        if (cleanContent.startsWith("/")) {
            cleanContent = cleanContent.substring(1);
        }

        // 分离标签名和其他属性
        int firstSpaceIndex = cleanContent.indexOf(' ');
        if (firstSpaceIndex != -1) {
            return cleanContent.substring(0, firstSpaceIndex).trim();
        }

        // 如果没有空格，返回整个内容（移除开头结尾的括号）
        if (cleanContent.startsWith("<") && cleanContent.endsWith(">")) {
            cleanContent = cleanContent.substring(1, cleanContent.length() - 1);
        }

        // 特殊处理自闭合标签末尾的 "/"
        if (cleanContent.endsWith("/")) {
            cleanContent = cleanContent.substring(0, cleanContent.length() - 1);
        }

        return cleanContent.trim();
    }

    /**
     * 根据位置计算行号
     */
    private static int calculateLineFromPosition(String htmlContent, int position) {
        if (position < 0 || position >= htmlContent.length()) {
            return 1;
        }

        int lineCount = 1;
        for (int i = 0; i <= position; i++) {
            if (htmlContent.charAt(i) == '\n') {
                lineCount++;
            }
        }
        return lineCount;
    }

    /**
     * 获取所有未闭合的标签信息
     */
    public static List<TagInfo> getUnclosedTags(String htmlContent) {
        ValidationResult result = analyzeTagClosure(htmlContent);
        return new ArrayList<>(result.getUnclosedTags());
    }

    /**
     * 检查HTML是否有效
     */
    public static boolean isValidHtml(String htmlContent) {
        ValidationResult result = analyzeTagClosure(htmlContent);
        return result.isValid();
    }

    /**
     * 打印验证结果详情
     */
    public static void printValidationDetails(String htmlContent) {
        System.out.println("=== HTML标签闭合分析 ===");
        ValidationResult result = analyzeTagClosure(htmlContent);

        System.out.println("验证状态: " + (result.isValid() ? "✓ 通过" : "✗ 失败"));

        if (!result.getUnclosedTags().isEmpty()) {
            System.out.println("\n未闭合标签:");
            for (TagInfo tag : result.getUnclosedTags()) {
                System.out.printf("  行 %d: <%s>%n", tag.getLineNumber(), tag.getTagName());
            }
        }

        if (!result.getErrors().isEmpty()) {
            System.out.println("\n错误信息:");
            for (String error : result.getErrors()) {
                System.out.println("  " + error);
            }
        }

        if (result.getUnclosedTags().isEmpty() && result.getErrors().isEmpty()) {
            System.out.println("\n✓ 所有标签都已正确闭合！");
        }

        System.out.println("=========================");
    }

    /**
     * 获取具体的未闭合标签名称列表
     * @param htmlContent HTML源码
     * @return 未闭合标签名称列表
     */
    public static List<String> getUnclosedTagNames(String htmlContent) {
        List<String> unclosedTagNames = new ArrayList<>();
        List<TagInfo> unclosedTags = getUnclosedTags(htmlContent);

        for (TagInfo tag : unclosedTags) {
            unclosedTagNames.add(tag.getTagName());
        }

        return unclosedTagNames;
    }
}
