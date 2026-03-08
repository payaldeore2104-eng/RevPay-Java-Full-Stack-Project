package com.revpay.model;

import javax.persistence.*;

@Entity
@Table(name = "user_security_answers")
public class UserSecurityAnswer {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "usr_ans_seq")
    @SequenceGenerator(name = "usr_ans_seq", sequenceName = "user_sec_answers_seq", allocationSize = 1)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "question_id", nullable = false)
    private SecurityQuestion question;

    @Column(name = "answer_hash", nullable = false)
    private String answerHash;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public SecurityQuestion getQuestion() {
        return question;
    }

    public void setQuestion(SecurityQuestion question) {
        this.question = question;
    }

    public String getAnswerHash() {
        return answerHash;
    }

    public void setAnswerHash(String answerHash) {
        this.answerHash = answerHash;
    }
}
