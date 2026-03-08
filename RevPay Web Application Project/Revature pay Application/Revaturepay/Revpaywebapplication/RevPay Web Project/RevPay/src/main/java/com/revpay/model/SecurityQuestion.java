package com.revpay.model;

import javax.persistence.*;

@Entity
@Table(name = "security_questions")
public class SecurityQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sec_q_seq")
    @SequenceGenerator(name = "sec_q_seq", sequenceName = "sec_questions_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false)
    private String question;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}
