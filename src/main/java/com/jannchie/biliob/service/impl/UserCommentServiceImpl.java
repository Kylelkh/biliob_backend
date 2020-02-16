package com.jannchie.biliob.service.impl;

import com.jannchie.biliob.constant.CreditConstant;
import com.jannchie.biliob.constant.ResultEnum;
import com.jannchie.biliob.credit.handle.CreditHandle;
import com.jannchie.biliob.model.Comment;
import com.jannchie.biliob.model.User;
import com.jannchie.biliob.service.UserCommentService;
import com.jannchie.biliob.utils.LoginChecker;
import com.jannchie.biliob.utils.Result;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;

/**
 * @author Jannchie
 */
@Service
public class UserCommentServiceImpl implements UserCommentService {
    private static final Logger logger = LogManager.getLogger(UserCommentServiceImpl.class);
    private static MongoTemplate mongoTemplate;
    private CreditHandle creditHandle;

    @Autowired
    public UserCommentServiceImpl(MongoTemplate mongoTemplate, CreditHandle creditHandle) {
        UserCommentServiceImpl.mongoTemplate = mongoTemplate;
        this.creditHandle = creditHandle;
    }


    @Override
    public List<Comment> listComments(String path, Integer page, Integer pageSize) {
        return mongoTemplate.find(
                Query.query(Criteria.where("path").is(path))
                        .with(PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "date"))), Comment.class);
    }

    @Override
    public ResponseEntity<Result<String>> postComment(Comment comment) {
        User user = LoginChecker.checkInfo();
        return creditHandle.doCreditOperation(user, CreditConstant.POST_COMMENT, () -> {
            comment.setDate(Calendar.getInstance().getTime());
            comment.setLikeList(new ArrayList<>());
            comment.setDisLikeList(new ArrayList<>());
            comment.setUserId(user.getId().toHexString());
            mongoTemplate.save(comment);
            return comment.getPath();
        });
    }

    @Override
    public ResponseEntity<Result<String>> likeComment(String commentId) {
        User user = LoginChecker.checkInfo();
        if (mongoTemplate.exists(Query.query(Criteria.where("likeList").is(user.getId().toHexString()).and("id").is(commentId)), Comment.class)) {
            return ResponseEntity.badRequest().body(new Result<>(ResultEnum.ALREADY_LIKE));
        }
        return creditHandle.doCreditOperation(user, CreditConstant.LIKE_COMMENT, () -> {
            mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(commentId)), new Update().addToSet("likeList", user.getId().toHexString()), Comment.class);
            String commentPublisherId = Objects.requireNonNull(mongoTemplate.findOne(Query.query(Criteria.where("_id").is(commentId)), Comment.class)).getUserId();
            User publisher = mongoTemplate.findOne(Query.query(Criteria.where("_id").is(commentPublisherId)), User.class);
            creditHandle.doCreditOperation(publisher, CreditConstant.BE_LIKE_COMMENT, () -> commentId);
            return commentId;
        });
    }

    @Override
    public ResponseEntity<Result<?>> dislikeComment(String commentId) {
        return null;
    }

    @Override
    public ResponseEntity<Result<?>> deleteComment(String commentId) {
        User user = LoginChecker.checkInfo();
        String commentPublisherId = Objects.requireNonNull(mongoTemplate.findOne(Query.query(Criteria.where("_id").is(commentId)), Comment.class)).getUserId();
        if (commentPublisherId.equals(user.getId().toHexString())) {
            mongoTemplate.remove(Query.query(Criteria.where("_id").is(commentId)), Comment.class);
            return ResponseEntity.ok(new Result<>(ResultEnum.SUCCEED));
        } else {
            return ResponseEntity.ok(new Result<>(ResultEnum.EXECUTE_FAILURE));
        }
    }
}