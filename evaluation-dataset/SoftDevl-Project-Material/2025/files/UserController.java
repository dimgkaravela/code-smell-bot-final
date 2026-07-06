package myy803.socialbookstore.controllers;


import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import myy803.socialbookstore.datamodel.Book;
import myy803.socialbookstore.datamodel.BookCategory;
import myy803.socialbookstore.datamodel.UserProfile;
import myy803.socialbookstore.datamodel.recomstrategies.RecommendationsFactory;
import myy803.socialbookstore.datamodel.recomstrategies.RecommendationsStrategy;
import myy803.socialbookstore.datamodel.searchstrategies.SearchFactory;
import myy803.socialbookstore.datamodel.searchstrategies.SearchStrategy;
import myy803.socialbookstore.formsdata.BookDto;
import myy803.socialbookstore.formsdata.RecommendationsDto;
import myy803.socialbookstore.formsdata.SearchDto;
import myy803.socialbookstore.formsdata.UserProfileDto;
import myy803.socialbookstore.mappers.BookAuthorMapper;
import myy803.socialbookstore.mappers.BookCategoryMapper;
import myy803.socialbookstore.mappers.BookMapper;
import myy803.socialbookstore.mappers.UserProfileMapper;
import myy803.socialbookstore.services.UserService;


@Controller
public class UserController {

	@Autowired
    UserService userService;
	
	@Autowired 
	BookCategoryMapper bookCategoryMapper;

	@Autowired
	private UserProfileMapper userProfileMapper;

	@Autowired
	private BookAuthorMapper bookAuthorMapper;
	
	@Autowired
	private BookCategoryMapper bookCategoriesMapper;
	
	@Autowired
	private BookMapper bookMapper;
	
	@Autowired
	private SearchFactory searchFactory;
	
	@Autowired
	private RecommendationsFactory recommendationsFactory;
		
    @RequestMapping("/user/dashboard")
    public String getUserMainMenu(){
       
    	return "user/dashboard";
    }
    
    @RequestMapping("/user/profile")
    public String retrieveProfile(Model model){
    	Authentication authentication = SecurityContextHolder.getContext().getAuthentication();	
    	String username = authentication.getName();
    	System.err.println("Logged use: " + username);
    	
    	List<BookCategory> categories = bookCategoryMapper.findAll();
    	model.addAttribute("categories", categories);
    	
    	Optional<UserProfile> optUserProfile = userProfileMapper.findById(username);
		UserProfile userProfile = null; 
		UserProfileDto userProfileDto = null;
		if(optUserProfile.isPresent()) {
			userProfile = optUserProfile.get();
			userProfileDto = userProfile.buildProfileDto();
		} else {
			userProfileDto = new UserProfileDto();
			userProfileDto.setUsername(username);
		}
		
    	model.addAttribute("profile", userProfileDto);
    	
    	return "user/profile";
    }
    
    @RequestMapping("/user/save_profile")
    public String saveProfile(@ModelAttribute("profile") UserProfileDto userProfileDto, Model theModel) {
    	System.err.println(userProfileDto.toString());
    	    	
    	Optional<UserProfile> optUserProfile = userProfileMapper.findById(userProfileDto.getUsername());
		UserProfile userProfile = null;
		if(optUserProfile.isPresent())	
			userProfile = optUserProfile.get();
		else
			userProfile = new UserProfile();
		
		userProfileDto.buildUserProfile(userProfile, bookAuthorMapper, bookCategoriesMapper);
		
		userProfileMapper.save(userProfile);
		
    	
    	return "user/dashboard";
    }
    
    @RequestMapping("/user/offers") 
    public String listBookOffers(Model model) {
    	Authentication authentication = SecurityContextHolder.getContext().getAuthentication();	
    	String username = authentication.getName();
    	    	
    	Optional<UserProfile> optUserProfile = userProfileMapper.findById(username);
		UserProfile userProfile = optUserProfile.get();
		List<BookDto> bookOffersDtos = userProfile.buildBookOffersDtos();
			
    	model.addAttribute("offers", bookOffersDtos);
    	
    	return "user/offers";
    }
    
    @RequestMapping("/user/show_offer_form") 
    public String showOfferForm(Model model) {
    	List<BookCategory> categories = bookCategoryMapper.findAll();
    	model.addAttribute("categories", categories);
    	model.addAttribute("offer", new BookDto());
    	
    	return "user/offer-form";
    }
    
    @RequestMapping("/user/save_offer") 
    public String saveOffer(@ModelAttribute("offer") BookDto bookOfferDto, Model model) {
    	Authentication authentication = SecurityContextHolder.getContext().getAuthentication();	
    	String username = authentication.getName();
    	    	
    	Optional<UserProfile> optUserProfile = userProfileMapper.findById(username);
		UserProfile userProfile = optUserProfile.get();
		userProfile.addBookOffer(bookOfferDto.buildBookOffer(bookAuthorMapper, bookCategoriesMapper));
		
		userProfileMapper.save(userProfile);
		
		return "redirect:/user/offers";
    }
    
    @RequestMapping("/user/search")
    public String showSearchForm(Model model) {
    	SearchDto searchDto = new SearchDto(); 
    	model.addAttribute("searchDto", searchDto);
    	
    	return "user/search_form";
    }
    
    @RequestMapping("/user/search_offer")
    public String search(@ModelAttribute("searchDto") SearchDto searchDto, Model model) {
    	    	
    	SearchStrategy searchStrategy = searchFactory.create(searchDto.getSelectedStrategy());
    	List<BookDto> bookDtos = searchStrategy.search(searchDto, bookMapper);
		
    	model.addAttribute("books", bookDtos);
    	return "user/search_results";
    }
    
    @RequestMapping("/user/recom")
    public String showRecommendationsForm(Model model) {
    	RecommendationsDto recomDto = new RecommendationsDto();
    	
    	model.addAttribute("recomDto", recomDto);
    	
    	return "user/recommendation_form";
    }
    
    @RequestMapping("/user/recommend_offers")
    public String recommend(@ModelAttribute("recomDto") RecommendationsDto recomDto, Model model) {
    	Authentication authentication = SecurityContextHolder.getContext().getAuthentication();	
    	String username = authentication.getName();
    	    	
    	RecommendationsStrategy recommendationsStrategy = recommendationsFactory.create(recomDto.getSelectedStrategy());
    	List<BookDto> bookDtos = recommendationsStrategy.recommend(username);
		
    	model.addAttribute("books", bookDtos);
    	
    	return "user/search_results";
    }
    
    @RequestMapping("/user/request_book")
    public String request(@RequestParam("selected_book_id") int bookId, Model model) {
    	Authentication authentication = SecurityContextHolder.getContext().getAuthentication();	
    	String username = authentication.getName();
    	    	
    	Optional<Book> requestedBook = bookMapper.findById(bookId);
		Optional<UserProfile> userProfile = userProfileMapper.findById(username);
		requestedBook.get().addRequestingUser(userProfile.get());
		bookMapper.save(requestedBook.get());
    	
    	return "redirect:/user/dashboard";
    }
    
    @RequestMapping("/user/requests")
    public String showUserBookRequests(Model model) {
    	Authentication authentication = SecurityContextHolder.getContext().getAuthentication();	
    	String username = authentication.getName();
    			
    	Optional<UserProfile> userProfile = userProfileMapper.findById(username);
		List <BookDto> requests = userProfile.get().buildBookRequestsDtos();
    	model.addAttribute("requests", requests);
    	
    	return "/user/book_requests";
    }

    @RequestMapping("/user/book_requesting_users")
    public String showRequestingUsersForBookOffer(@RequestParam("selected_offer_id") int bookId, Model model) {
    	    	
    	Optional<Book> book = bookMapper.findById(bookId);
    	List<UserProfileDto> requestingUsers = book.get().getRequestingUserProfileDtos();
    	
    	model.addAttribute("requesting_users", requestingUsers);
    	model.addAttribute("book_id", bookId);
   
    	return "/user/requesting_users";
    }
    
    @RequestMapping("/user/accept_request")
    public String acceptRequest(@RequestParam("selected_user") String username, @RequestParam("book_id") int bookId, Model model) {
    	/*
    	 * TODO - have to implement this user story
    	 */
    	System.err.println("Selected user: " + username + " for book id: " + bookId);
    	
    	return "redirect:/user/dashboard";
    }
    
    @RequestMapping("/user/delete_book_offer")
    public String deleteBookOffer(@RequestParam("selected_offer_id") int bookId, Model model) {
    	/*
    	 * TODO - have to implement this user story
    	 */
    	System.err.println("Delete Book Offer for book id: " + bookId);

    	return "redirect:/user/dashboard";
    }
    
    @RequestMapping("/user/delete_book_request")
    public String deleteBookRequest(@RequestParam("selected_request_id") int bookId, Model model) {
    	/*
    	 * TODO - have to implement this user story
    	 */
    	System.err.println("Delete Book Request for book id: " + bookId);

    	return "redirect:/user/dashboard";
    }

}
