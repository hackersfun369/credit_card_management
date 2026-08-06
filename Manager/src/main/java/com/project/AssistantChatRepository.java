package com.project;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface AssistantChatRepository extends JpaRepository<AssistantChatMessage, Long> {
 List<AssistantChatMessage> findByManagerUsernameAndConversationIdOrderByCreatedAtAsc(String managerUsername, String conversationId);
    AssistantChatMessage findFirstByManagerUsernameAndConversationIdAndRoleOrderByCreatedAtAsc(String managerUsername, String conversationId, String role);
 @Query("select m.conversationId from AssistantChatMessage m where m.managerUsername = :username group by m.conversationId order by max(m.createdAt) desc")
 List<String> findConversationIdsByManagerUsername(@Param("username") String username);

    @Transactional
    long deleteByManagerUsernameAndConversationId(String managerUsername, String conversationId);
}