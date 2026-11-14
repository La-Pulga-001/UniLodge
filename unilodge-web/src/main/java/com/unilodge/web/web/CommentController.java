package com.unilodge.web.web;

import com.unilodge.common.dto.CommentDto;
import com.unilodge.common.dto.CommentRequest;
import com.unilodge.common.dto.MessageResponse;
import com.unilodge.common.dto.PagedResponse;
import com.unilodge.service.post.CommentService;
import com.unilodge.web.security.AuthSupport;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Comments attached to student posts. */
@RestController
@RequestMapping("/api")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping("/posts/{postId}/comments")
    public PagedResponse<CommentDto> list(Authentication authentication,
                                          @PathVariable UUID postId,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "50") int size) {
        return commentService.list(AuthSupport.current(authentication), postId, page, size);
    }

    @PostMapping("/posts/{postId}/comments")
    public CommentDto add(Authentication authentication,
                          @PathVariable UUID postId,
                          @Valid @RequestBody CommentRequest request) {
        return commentService.add(AuthSupport.require(authentication), postId, request);
    }

    @DeleteMapping("/comments/{commentId}")
    public MessageResponse remove(Authentication authentication, @PathVariable UUID commentId) {
        commentService.removeOwn(AuthSupport.require(authentication), commentId);
        return new MessageResponse("Your comment has been removed.");
    }
}
