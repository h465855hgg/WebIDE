package com.web.webide.html;

import java.util.ArrayList;
import java.util.List;

public class HtmlRecordLabel {
    List<HtmlLexer.Token> tokens;
    public HtmlRecordLabel(String content){
        this.tokens=HtmlLexer.tokenize(content);
    }
    public String findLabel(){
        List<String> list=new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).getType()== HtmlLexer.TokenType.TAG_OPEN){
                list.add(tokens.get(i).getValue());
            } else if (tokens.get(i).getType()== HtmlLexer.TokenType.TAG_CLOSE) {
                list.remove(list.size()-1);
            }
        }
        return list.isEmpty()?"null":list.get(list.size()-1);
    }
    public String isBlock(){
        StringBuilder stringBuilder=new StringBuilder();
        if ((tokens.isEmpty()?"null":tokens.get(tokens.size()-1).getValue()).startsWith("<")){
            for (int i = 1; i < tokens.get(tokens.size()-1).getValue().length(); i++) {
                if (Character.isLetter(tokens.get(tokens.size()-1).getValue().charAt(i))){
                    stringBuilder.append(tokens.get(tokens.size()-1).getValue().charAt(i));
                }else {
                    break;
                }
            }
        }
        return stringBuilder.length()==0 ?"null":stringBuilder.toString();
    }
}