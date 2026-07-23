package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class QuizSubmitRequest {
    @NotBlank private String quizId;
    private List<QuizAnswerItem> answers;
    private int timeTakenSeconds;

    public String getQuizId() { return quizId; }
    public void setQuizId(String quizId) { this.quizId = quizId; }
    public List<QuizAnswerItem> getAnswers() { return answers; }
    public void setAnswers(List<QuizAnswerItem> answers) { this.answers = answers; }
    public int getTimeTakenSeconds() { return timeTakenSeconds; }
    public void setTimeTakenSeconds(int timeTakenSeconds) { this.timeTakenSeconds = timeTakenSeconds; }

    public static class QuizAnswerItem {
        private String questionId;
        private List<String> answer;

        public String getQuestionId() { return questionId; }
        public void setQuestionId(String questionId) { this.questionId = questionId; }
        public List<String> getAnswer() { return answer; }
        public void setAnswer(List<String> answer) { this.answer = answer; }
    }
}
