package com.unilodge.service.post;

import com.unilodge.common.dto.PagedResponse;
import com.unilodge.common.dto.StudentPostDto;
import com.unilodge.common.dto.StudentPostRequest;
import com.unilodge.common.enums.ModerationStatus;
import com.unilodge.common.enums.UserRole;
import com.unilodge.common.exception.BadRequestException;
import com.unilodge.common.exception.ForbiddenException;
import com.unilodge.common.exception.NotFoundException;
import com.unilodge.common.exception.UnauthorizedException;
import com.unilodge.persistence.entity.SavedStudentPost;
import com.unilodge.persistence.entity.StudentPost;
import com.unilodge.persistence.entity.User;
import com.unilodge.persistence.repository.CommentRepository;
import com.unilodge.persistence.repository.SavedStudentPostRepository;
import com.unilodge.persistence.repository.StudentPostRepository;
import com.unilodge.persistence.repository.UserRepository;
import com.unilodge.persistence.specification.StudentPostSpecifications;
import com.unilodge.service.mapper.DtoMapper;
import com.unilodge.service.university.UniversityService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Roommate requests and housing posts written by students. */
@Service
public class StudentPostService {

    private final StudentPostRepository postRepository;
    private final SavedStudentPostRepository savedRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final UniversityService universityService;

    public StudentPostService(StudentPostRepository postRepository,
                              SavedStudentPostRepository savedRepository,
                              CommentRepository commentRepository,
                              UserRepository userRepository,
                              UniversityService universityService) {
        this.postRepository = postRepository;
        this.savedRepository = savedRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.universityService = universityService;
    }

    @Transactional(readOnly = true)
    public PagedResponse<StudentPostDto> search(User requester,
                                                ModerationStatus status,
                                                String q,
                                                UUID universityId,
                                                UUID authorId,
                                                int page,
                                                int size) {
        requireBoardAccess(requester);
        Page<StudentPost> result = postRepository.findAll(
                StudentPostSpecifications.withFilters(status, q, universityId, authorId),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                        Sort.by(Sort.Direction.DESC, "createdAt")));

        Set<UUID> savedIds = savedIds(requester);
        Map<UUID, Long> commentCounts = commentCounts(result.getContent());
        List<StudentPostDto> content = result.getContent().stream()
                .map(post -> DtoMapper.post(post,
                        commentCounts.getOrDefault(post.getId(), 0L),
                        savedIds.contains(post.getId())))
                .toList();
        return PagedResponse.of(content, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public StudentPostDto getDetail(User requester, UUID id) {
        requireBoardAccess(requester);
        StudentPost post = postRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("That student post could not be found."));

        boolean author = requester != null && post.getAuthor().getId().equals(requester.getId());
        boolean admin = requester != null && requester.getRole() == UserRole.ADMIN
                && requester.isAdminVerified();
        if (post.getStatus() != ModerationStatus.VISIBLE && !author && !admin) {
            throw new NotFoundException("That student post could not be found.");
        }

        long commentCount = commentRepository.countByPostIdAndStatus(id, ModerationStatus.VISIBLE);
        boolean saved = requester != null
                && savedRepository.existsByUserIdAndPostId(requester.getId(), id);
        return DtoMapper.post(post, commentCount, saved);
    }

    @Transactional
    public StudentPostDto create(User student, StudentPostRequest request) {
        if (student.getRole() != UserRole.STUDENT) {
            throw new ForbiddenException("Only students can create roommate requests.");
        }
        StudentPost post = new StudentPost();
        post.setAuthor(student);
        post.setUniversity(universityService.requireActive(request.universityId()));
        post.setTitle(request.title().trim());
        post.setBody(request.body().trim());
        post.setPreferredLocation(trimToNull(request.preferredLocation()));
        post.setBudgetMax(request.budgetMax());
        post.setRoommatesNeeded(request.roommatesNeeded() == null ? 1 : request.roommatesNeeded());
        post.setStatus(ModerationStatus.VISIBLE);
        postRepository.save(post);
        return DtoMapper.post(post, 0, false);
    }

    @Transactional
    public StudentPostDto update(User student, UUID id, StudentPostRequest request) {
        StudentPost post = postRepository.findByIdAndAuthorId(id, student.getId())
                .orElseThrow(() -> new NotFoundException("That student post could not be found."));
        if (post.getStatus() == ModerationStatus.REMOVED) {
            throw new BadRequestException("This post has been removed and cannot be edited.");
        }
        post.setUniversity(universityService.requireActive(request.universityId()));
        post.setTitle(request.title().trim());
        post.setBody(request.body().trim());
        post.setPreferredLocation(trimToNull(request.preferredLocation()));
        post.setBudgetMax(request.budgetMax());
        post.setRoommatesNeeded(request.roommatesNeeded() == null ? 1 : request.roommatesNeeded());
        postRepository.save(post);
        long commentCount = commentRepository.countByPostIdAndStatus(id, ModerationStatus.VISIBLE);
        boolean saved = savedRepository.existsByUserIdAndPostId(student.getId(), id);
        return DtoMapper.post(post, commentCount, saved);
    }

    @Transactional
    public void remove(User student, UUID id) {
        StudentPost post = postRepository.findByIdAndAuthorId(id, student.getId())
                .orElseThrow(() -> new NotFoundException("That student post could not be found."));
        post.setStatus(ModerationStatus.REMOVED);
        postRepository.save(post);
    }

    @Transactional
    public boolean toggleSaved(User student, UUID postId) {
        if (student.getRole() != UserRole.STUDENT) {
            throw new ForbiddenException("Only student accounts can save posts.");
        }
        StudentPost post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("That student post could not be found."));
        if (post.getStatus() != ModerationStatus.VISIBLE) {
            throw new BadRequestException("Only published posts can be saved.");
        }
        var existing = savedRepository.findByUserIdAndPostId(student.getId(), postId);
        if (existing.isPresent()) {
            savedRepository.delete(existing.get());
            return false;
        }
        SavedStudentPost saved = new SavedStudentPost();
        saved.setUser(student);
        saved.setPost(post);
        savedRepository.save(saved);
        return true;
    }

    @Transactional(readOnly = true)
    public List<StudentPostDto> savedPosts(User student) {
        requireStudent(student);
        return savedRepository.findByUserIdOrderByCreatedAtDesc(student.getId()).stream()
                .map(SavedStudentPost::getPost)
                .filter(post -> post.getStatus() == ModerationStatus.VISIBLE)
                .map(post -> DtoMapper.post(post,
                        commentRepository.countByPostIdAndStatus(post.getId(), ModerationStatus.VISIBLE), true))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UUID> savedIdsFor(User student) {
        requireStudent(student);
        return savedRepository.findByUserIdOrderByCreatedAtDesc(student.getId()).stream()
                .map(saved -> saved.getPost().getId())
                .toList();
    }

    private Set<UUID> savedIds(User requester) {
        if (requester == null || requester.getRole() != UserRole.STUDENT) {
            return Set.of();
        }
        return new HashSet<>(savedIdsFor(requester));
    }

    /** The board is a students-only space; administrators join to moderate it. */
    private void requireBoardAccess(User requester) {
        if (requester == null) {
            throw new UnauthorizedException("Please sign in to view the student board.");
        }
        if (requester.getRole() != UserRole.STUDENT && requester.getRole() != UserRole.ADMIN) {
            throw new ForbiddenException("The student board is only available to students.");
        }
    }

    private void requireStudent(User user) {
        if (user == null || user.getRole() != UserRole.STUDENT) {
            throw new ForbiddenException("Only student accounts can use saved board posts.");
        }
    }

    private Map<UUID, Long> commentCounts(List<StudentPost> posts) {
        Map<UUID, Long> counts = new HashMap<>();
        if (posts.isEmpty()) {
            return counts;
        }
        List<UUID> ids = posts.stream().map(StudentPost::getId).toList();
        for (Object[] row : commentRepository.countVisibleByPostIds(ids)) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
