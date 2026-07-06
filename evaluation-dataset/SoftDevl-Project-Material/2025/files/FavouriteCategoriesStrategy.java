package myy803.socialbookstore.datamodel.recomstrategies;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import myy803.socialbookstore.datamodel.UserProfile;
import myy803.socialbookstore.formsdata.BookDto;
import myy803.socialbookstore.mappers.UserProfileMapper;

@Component
public class FavouriteCategoriesStrategy implements RecommendationsStrategy {
	
	@Autowired
	private UserProfileMapper userProfileMapper;

	public List<BookDto> recommend(String username) {
		UserProfile userProfile = userProfileMapper.findByUsername(username);
		List<BookDto> bookDtos = retrieveRecommendedBooks(userProfile);
	
		return bookDtos;
	}
	
	protected List<BookDto> retrieveRecommendedBooks(UserProfile userProfile) {
		List<BookDto> bookDtos = userProfile.getBooksOfFavouriteCategories();
		return bookDtos;
	}
}
