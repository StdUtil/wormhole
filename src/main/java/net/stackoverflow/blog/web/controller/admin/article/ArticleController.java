package net.stackoverflow.blog.web.controller.admin.article;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import net.stackoverflow.blog.common.BaseController;
import net.stackoverflow.blog.common.Page;
import net.stackoverflow.blog.common.Result;
import net.stackoverflow.blog.exception.BusinessException;
import net.stackoverflow.blog.pojo.entity.Article;
import net.stackoverflow.blog.pojo.vo.ArticleVO;
import net.stackoverflow.blog.service.ArticleService;
import net.stackoverflow.blog.service.CategoryService;
import net.stackoverflow.blog.service.CommentService;
import net.stackoverflow.blog.service.UserService;
import net.stackoverflow.blog.util.CollectionUtils;
import net.stackoverflow.blog.util.DateUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.HtmlUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * 文章管理接口
 *
 * @author 凉衫薄
 */
@Api(description = "文章管理")
@Controller
@RequestMapping(value = "/admin/article")
public class ArticleController extends BaseController {

    @Autowired
    private ArticleService articleService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private UserService userService;
    @Autowired
    private CommentService commentService;

    /**
     * 文章管理页面跳转
     *
     * @return ModelAndView对象
     */
    @ApiOperation(value = "文章管理页面跳转")
    @RequestMapping(value = "/article_management", method = RequestMethod.GET)
    public ModelAndView management() {
        ModelAndView mv = new ModelAndView();
        mv.setViewName("/admin/article/article_management");
        return mv;
    }

    /**
     * 通过url获取code
     *
     * @param url 文章url
     * @return 文章编码code
     */
    private String urlToCode(String url) {
        String[] paths = url.split("/");
        return paths[paths.length - 1];
    }

    /**
     * 通过code转url
     *
     * @param code 文章编码code
     * @return 文章url
     */
    private String codeToUrl(String code) {
        return "/article" + DateUtils.getDatePath() + code;
    }


    /**
     * 更新文章
     *
     * @param articleVO 文章VO对象
     * @return ResponseEntity对象
     */
    @ApiOperation(value = "文章更新接口", response = Result.class)
    @RequestMapping(value = "/update_article", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity update(@ApiParam(name = "articleVO", value = "文章VO对象") @Validated(ArticleVO.UpdateGroup.class) @RequestBody ArticleVO articleVO) {

        Article article = articleService.selectById(articleVO.getId());
        if (article == null) {
            throw new BusinessException("未找到文章");
        }

        //校验url是否重复
        String[] paths = article.getUrl().split("/");
        paths[paths.length - 1] = articleVO.getArticleCode();
        String url = String.join("/", paths);

        if (!urlToCode(article.getUrl()).equals(articleVO.getArticleCode()) && (articleService.selectByUrl(url) != null)) {
            throw new BusinessException("url重复");
        }

        Article updateArticle = new Article();
        BeanUtils.copyProperties(articleVO, updateArticle);
        updateArticle.setModifyDate(new Date());
        updateArticle.setUrl(url);
        articleService.update(updateArticle);

        Result result = new Result();
        result.setStatus(Result.SUCCESS);
        result.setMessage("文章更新成功");

        return new ResponseEntity(result, HttpStatus.OK);
    }

    /**
     * 分页获取文章列表
     *
     * @param page  当前页码，从1开始
     * @param limit 每页数量
     * @return ResponseEntity对象
     */
    @ApiOperation(value = "分页获取文章列表", response = Result.class)
    @RequestMapping(value = "/list_article", method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity list(@ApiParam(name = "page", value = "当前页码,从1开始") @RequestParam(value = "page") String page,
                               @ApiParam(name = "limit", value = "每页数量") @RequestParam(value = "limit") String limit) {

        Page pageParam = new Page(Integer.valueOf(page), Integer.valueOf(limit), null);
        List<Article> articles = articleService.selectByPage(pageParam);

        int count = articleService.selectByCondition(new HashMap<>(16)).size();
        List<ArticleVO> articleVOs = new ArrayList<>();

        for (Article article : articles) {
            ArticleVO articleVO = new ArticleVO();
            BeanUtils.copyProperties(article, articleVO);
            articleVO.setTitle(HtmlUtils.htmlEscape(article.getTitle()));
            articleVO.setAuthor(HtmlUtils.htmlEscape(userService.selectById(article.getUserId()).getNickname()));
            articleVO.setCategoryName(categoryService.selectById(article.getCategoryId()).getName());
            articleVO.setCommentCount(commentService.selectByCondition(new HashMap<String, Object>(16) {{
                put("articleId", article.getId());
            }}).size());
            articleVO.setUrl(article.getUrl());
            if (article.getVisible() == 0) {
                articleVO.setVisibleStr("否");
            } else {
                articleVO.setVisibleStr("是");
            }
            articleVOs.add(articleVO);
        }

        Map<String, Object> map = new HashMap<>(16);
        map.put("count", count);
        map.put("items", articleVOs);

        Result result = new Result();
        result.setStatus(Result.SUCCESS);
        result.setMessage("查询成功");
        result.setData(map);
        return new ResponseEntity(result, HttpStatus.OK);
    }

    /**
     * 删除文章
     *
     * @param ids 被删除文章主键列表
     * @return ResponseEntity对象
     */
    @ApiOperation(value = "删除文章", response = Result.class)
    @RequestMapping(value = "/delete_article", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity delete(@ApiParam(value = "被删除文章的主键列表") @RequestBody List<String> ids) {

        if (CollectionUtils.isEmpty(ids)) {
            throw new BusinessException("文章主键不能为空");
        }

        articleService.batchDelete(ids);

        Result result = new Result();
        result.setStatus(Result.SUCCESS);
        result.setMessage("删除成功");

        return new ResponseEntity(result, HttpStatus.OK);
    }

    /**
     * 设置文章是否可见
     *
     * @param ids 文章主键列表
     * @return ResponseEntity对象
     */
    @ApiOperation(value = "设置文章是否可见")
    @RequestMapping(value = "/visible_article", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity visible(@RequestBody List<String> ids,
                                  @ApiParam(value = "显示或隐藏,0-隐藏,1-显示") @RequestParam(name = "visible") Integer visible) {

        if (CollectionUtils.isEmpty(ids)) {
            throw new BusinessException("文章主键不能为空");
        }

        List<Article> articles = articleService.selectByIds(ids);

        for (Article article : articles) {
            article.setVisible(visible);
        }

        articleService.batchUpdate(articles);

        Result result = new Result();
        if (visible == 0) {
            result.setMessage("隐藏成功");
            result.setStatus(Result.SUCCESS);
        } else {
            result.setMessage("显示成功");
            result.setStatus(Result.SUCCESS);
        }
        return new ResponseEntity(result, HttpStatus.OK);
    }

    /**
     * 导出markdown格式备份
     *
     * @param id 文章主键
     * @return ResponseEntity对象
     * @throws IOException
     */
    @ApiOperation(value = "导出markdown格式备份文件", response = ResponseEntity.class)
    @RequestMapping(value = "/export_article", method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<byte[]> export(@ApiParam(name = "id", value = "文章主键") @RequestParam("id") String id) throws IOException {
        Article article = articleService.selectById(id);
        String filename = article.getTitle() + ".md";
        filename = new String(filename.getBytes("UTF-8"), "ISO-8859-1");

        InputStream is = new ByteArrayInputStream(article.getArticleMd().getBytes("UTF-8"));
        byte[] body = new byte[is.available()];
        is.read(body);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment;filename=" + filename);
        ResponseEntity<byte[]> entity = new ResponseEntity<>(body, headers, HttpStatus.OK);
        return entity;
    }
}
