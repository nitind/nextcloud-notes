package it.niedermann.owncloud.notes.main;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;

import it.niedermann.owncloud.notes.R;
import it.niedermann.owncloud.notes.main.NavigationAdapter.NavigationItem;
import it.niedermann.owncloud.notes.persistence.NotesDatabase;
import it.niedermann.owncloud.notes.persistence.entity.Account;
import it.niedermann.owncloud.notes.persistence.entity.Category;
import it.niedermann.owncloud.notes.persistence.entity.NoteWithCategory;
import it.niedermann.owncloud.notes.shared.model.CategorySortingMethod;
import it.niedermann.owncloud.notes.shared.model.Item;
import it.niedermann.owncloud.notes.shared.model.NavigationCategory;

import static androidx.lifecycle.Transformations.distinctUntilChanged;
import static androidx.lifecycle.Transformations.map;
import static it.niedermann.owncloud.notes.main.MainActivity.ADAPTER_KEY_RECENT;
import static it.niedermann.owncloud.notes.main.MainActivity.ADAPTER_KEY_STARRED;
import static it.niedermann.owncloud.notes.main.slots.SlotterUtil.fillListByCategory;
import static it.niedermann.owncloud.notes.main.slots.SlotterUtil.fillListByInitials;
import static it.niedermann.owncloud.notes.main.slots.SlotterUtil.fillListByTime;
import static it.niedermann.owncloud.notes.shared.model.ENavigationCategoryType.FAVORITES;
import static it.niedermann.owncloud.notes.shared.model.ENavigationCategoryType.RECENT;
import static it.niedermann.owncloud.notes.shared.util.DisplayUtils.convertToCategoryNavigationItem;

public class MainViewModel extends AndroidViewModel {

    private static final String TAG = MainViewModel.class.getSimpleName();

    @NonNull
    private NotesDatabase db;

    @NonNull
    private MutableLiveData<Account> currentAccount = new MutableLiveData<>();
    @NonNull
    private MutableLiveData<String> searchTerm = new MutableLiveData<>();
    @NonNull
    private MutableLiveData<Void> sortOrderOfSpecialNavigationCategoryChanged = new MutableLiveData<>();
    @NonNull
    private MutableLiveData<NavigationCategory> selectedCategory = new MutableLiveData<>(new NavigationCategory(RECENT));

    public MainViewModel(@NonNull Application application) {
        super(application);
        this.db = NotesDatabase.getInstance(application.getApplicationContext());
    }

    public void postCurrentAccount(@NonNull Account account) {
        this.currentAccount.postValue(account);
    }

    public void postSearchTerm(String searchTerm) {
        this.searchTerm.postValue(searchTerm);
    }

    public void postSelectedCategory(@NonNull NavigationCategory selectedCategory) {
        this.selectedCategory.postValue(selectedCategory);
    }

    public void postSortOrderOfSpecialNavigationCategoryChanged() {
        this.sortOrderOfSpecialNavigationCategoryChanged.postValue(null);
    }

    @NonNull
    public LiveData<Account> getCurrentAccount() {
        return currentAccount;
    }

    @NonNull
    public LiveData<String> getSearchTerm() {
        return searchTerm;
    }

    @NonNull
    public LiveData<NavigationCategory> getSelectedCategory() {
        return selectedCategory;
    }

    @NonNull
    public LiveData<Void> filterChanged() {
        MediatorLiveData<Void> mediatorLiveData = new MediatorLiveData<>();
        mediatorLiveData.addSource(currentAccount, (o) -> mediatorLiveData.postValue(null));
        mediatorLiveData.addSource(searchTerm, (o) -> mediatorLiveData.postValue(null));
        mediatorLiveData.addSource(selectedCategory, (o) -> mediatorLiveData.postValue(null));
        mediatorLiveData.addSource(sortOrderOfSpecialNavigationCategoryChanged, (o) -> mediatorLiveData.postValue(null));
        return mediatorLiveData;
    }

    @NonNull
    public LiveData<List<Item>> getNotesListLiveData() {
        Account currentAccount = getCurrentAccount().getValue();
        NavigationCategory selectedCategory = getSelectedCategory().getValue();
        LiveData<List<NoteWithCategory>> fromDatabase;
        if (currentAccount != null && selectedCategory != null) {
            Long accountId = currentAccount.getId();
            CategorySortingMethod sortingMethod = db.getCategoryOrder(selectedCategory);
            String searchQuery = getSearchTerm().getValue();
            searchQuery = searchQuery == null ? "%" : "%" + searchQuery.trim() + "%";
            switch (selectedCategory.getType()) {
                case FAVORITES: {
                    fromDatabase = db.getNoteDao().searchFavorites(accountId, searchQuery, sortingMethod.getSorder());
                    break;
                }
                case UNCATEGORIZED: {
                    fromDatabase = db.getNoteDao().searchUncategorized(accountId, searchQuery, sortingMethod.getSorder());
                    break;
                }
                case RECENT: {
                    fromDatabase = db.getNoteDao().searchRecent(accountId, searchQuery, sortingMethod.getSorder());
                    break;
                }
                case DEFAULT_CATEGORY:
                default: {
                    Category category = selectedCategory.getCategory();
                    fromDatabase = db.getNoteDao().searchByCategory(accountId, searchQuery, category == null ? "" : category.getTitle(), sortingMethod.getSorder());
                    break;
                }
            }

            return distinctUntilChanged(
                    map(fromDatabase, noteList -> {
                        //noinspection SwitchStatementWithTooFewBranches
                        switch (selectedCategory.getType()) {
                            case DEFAULT_CATEGORY: {
                                Category category = selectedCategory.getCategory();
                                if (category != null) {
                                    return fillListByCategory(noteList, category.getTitle());
                                } else {
                                    Log.e(TAG, "Tried to fill list by category, but category is null.");
                                }
                            }
                            default: {
                                if (sortingMethod == CategorySortingMethod.SORT_MODIFIED_DESC) {
                                    return fillListByTime(getApplication(), noteList);
                                } else {
                                    return fillListByInitials(getApplication(), noteList);
                                }
                            }
                        }
                    })
            );
        } else {
            return new MutableLiveData<>();
        }
    }

    @NonNull
    public LiveData<List<NavigationItem>> getNavigationCategories(String navigationOpen) {
        Account currentAccount = getCurrentAccount().getValue();
        NavigationCategory selectedCategory = getSelectedCategory().getValue();
        if (currentAccount != null && selectedCategory != null) {
            return distinctUntilChanged(
                    map(db.getCategoryDao().getCategoriesLiveData(currentAccount.getId()), fromDatabase -> {
                        List<NavigationAdapter.CategoryNavigationItem> categories = convertToCategoryNavigationItem(getApplication(), db.getCategoryDao().getCategories(currentAccount.getId()));
                        NavigationItem itemRecent = new NavigationItem(ADAPTER_KEY_RECENT, getApplication().getString(R.string.label_all_notes), db.getNoteDao().count(currentAccount.getId()), R.drawable.ic_access_time_grey600_24dp, RECENT);
                        NavigationItem itemFavorites = new NavigationItem(ADAPTER_KEY_STARRED, getApplication().getString(R.string.label_favorites), db.getNoteDao().getFavoritesCount(currentAccount.getId()), R.drawable.ic_star_yellow_24dp, FAVORITES);

                        ArrayList<NavigationItem> items = new ArrayList<>(fromDatabase.size() + 3);
                        items.add(itemRecent);
                        items.add(itemFavorites);
                        NavigationItem lastPrimaryCategory = null;
                        NavigationItem lastSecondaryCategory = null;
                        for (NavigationItem item : categories) {
                            int slashIndex = item.label.indexOf('/');
                            String currentPrimaryCategory = slashIndex < 0 ? item.label : item.label.substring(0, slashIndex);
                            String currentSecondaryCategory = null;
                            boolean isCategoryOpen = currentPrimaryCategory.equals(navigationOpen);

                            if (isCategoryOpen && !currentPrimaryCategory.equals(item.label)) {
                                String currentCategorySuffix = item.label.substring(navigationOpen.length() + 1);
                                int subSlashIndex = currentCategorySuffix.indexOf('/');
                                currentSecondaryCategory = subSlashIndex < 0 ? currentCategorySuffix : currentCategorySuffix.substring(0, subSlashIndex);
                            }

                            boolean belongsToLastPrimaryCategory = lastPrimaryCategory != null && currentPrimaryCategory.equals(lastPrimaryCategory.label);
                            boolean belongsToLastSecondaryCategory = belongsToLastPrimaryCategory && lastSecondaryCategory != null && lastSecondaryCategory.label.equals(currentPrimaryCategory + "/" + currentSecondaryCategory);

                            if (isCategoryOpen && !belongsToLastPrimaryCategory && currentSecondaryCategory != null) {
                                lastPrimaryCategory = new NavigationItem("category:" + currentPrimaryCategory, currentPrimaryCategory, 0, NavigationAdapter.ICON_MULTIPLE_OPEN);
                                items.add(lastPrimaryCategory);
                                belongsToLastPrimaryCategory = true;
                            }

                            if (belongsToLastPrimaryCategory && belongsToLastSecondaryCategory) {
                                lastSecondaryCategory.count += item.count;
                                lastSecondaryCategory.icon = NavigationAdapter.ICON_SUB_MULTIPLE;
                            } else if (belongsToLastPrimaryCategory) {
                                if (isCategoryOpen) {
                                    item.label = currentPrimaryCategory + "/" + currentSecondaryCategory;
                                    item.id = "category:" + item.label;
                                    item.icon = NavigationAdapter.ICON_SUB_FOLDER;
                                    items.add(item);
                                    lastSecondaryCategory = item;
                                } else {
                                    lastPrimaryCategory.count += item.count;
                                    lastPrimaryCategory.icon = NavigationAdapter.ICON_MULTIPLE;
                                    lastSecondaryCategory = null;
                                }
                            } else {
                                if (isCategoryOpen) {
                                    item.icon = NavigationAdapter.ICON_MULTIPLE_OPEN;
                                } else {
                                    item.label = currentPrimaryCategory;
                                    item.id = "category:" + item.label;
                                }
                                items.add(item);
                                lastPrimaryCategory = item;
                                lastSecondaryCategory = null;
                            }
                        }
                        return items;
                    })
            );
        } else {
            return new MutableLiveData<>();
        }
    }

}
