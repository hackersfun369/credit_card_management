package com.project;
import java.time.Instant;
import jakarta.persistence.*;
@Entity
@Table(name = "ASSISTANT_CHAT_MESSAGE", indexes = @Index(name = "idx_assistant_chat_owner_conversation", columnList = "manager_username, conversation_id, created_at"))
public class AssistantChatMessage {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
 @Column(name = "manager_username", nullable = false, length = 100) private String managerUsername;
 @Column(name = "conversation_id", nullable = false, length = 64) private String conversationId;
 @Column(nullable = false, length = 16) private String role;
 @Column(nullable = false, length = 4000) private String content;
 @Column(name = "created_at", nullable = false) private Instant createdAt;
 public Long getId(){return id;} public String getManagerUsername(){return managerUsername;} public void setManagerUsername(String v){managerUsername=v;}
 public String getConversationId(){return conversationId;} public void setConversationId(String v){conversationId=v;}
 public String getRole(){return role;} public void setRole(String v){role=v;} public String getContent(){return content;} public void setContent(String v){content=v;}
 public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
}