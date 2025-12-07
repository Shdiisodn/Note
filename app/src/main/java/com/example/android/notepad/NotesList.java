/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.notepad;

import com.example.android.notepad.NotePad;

import android.app.ListActivity;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.text.format.DateFormat;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.ContentValues;
import android.widget.SearchView;
import java.util.ArrayList;
import android.graphics.Paint;




/**
 * Displays a list of notes. Will display notes from the {@link Uri}
 * provided in the incoming Intent if there is one, otherwise it defaults to displaying the
 * contents of the {@link NotePadProvider}.
 *
 * NOTE: Notice that the provider operations in this Activity are taking place on the UI thread.
 * This is not a good practice. It is only done here to make the code more readable. A real
 * application should use the {@link android.content.AsyncQueryHandler} or
 * {@link android.os.AsyncTask} object to perform operations asynchronously on a separate thread.
 */
public class NotesList extends ListActivity {

    // For logging and debugging
    private static final String TAG = "NotesList";

    // 需要从数据库查询的列
    private static final String[] PROJECTION = new String[] {
            NotePad.Notes._ID,                       // 0
            NotePad.Notes.COLUMN_NAME_TITLE,         // 1
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, // 2
            NotePad.Notes.COLUMN_NAME_CATEGORY,      // 3 —— 分类
            NotePad.Notes.COLUMN_NAME_IS_TODO,       // 4 —— 是否待办
            NotePad.Notes.COLUMN_NAME_IS_DONE        // 5 —— 是否已完成
    };

    private SimpleCursorAdapter mAdapter;      // 统一保存适配器
    private String mCurrentCategoryFilter;     // 当前正在使用的分类过滤（null 表示全部）
    private String mCurrentQuery = null;       // 当前的搜索关键字，null 表示不搜索

    // 待办筛选：0=全部，1=只看待办(未完成)，2=只看已完成
    private static final int TODO_FILTER_ALL = 0;
    private static final int TODO_FILTER_ONLY_TODO = 1;
    private static final int TODO_FILTER_ONLY_DONE = 2;
    private int mCurrentTodoFilter = TODO_FILTER_ALL;

    /** 标题列的下标 */
    private static final int COLUMN_INDEX_TITLE = 1;
    /** 修改时间列的下标 */
    private static final int COLUMN_INDEX_MODIFICATION_DATE = 2;
    /** 分类列的下标 */
    private static final int COLUMN_INDEX_CATEGORY = 3;
    /** 是否待办列的下标 */
    private static final int COLUMN_INDEX_IS_TODO = 4;
    /** 是否完成列的下标 */
    private static final int COLUMN_INDEX_IS_DONE = 5;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // The user does not need to hold down the key to use menu shortcuts.
        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);

        /* If no data is given in the Intent that started this Activity, then this Activity
         * was started when the intent filter matched a MAIN action. We should use the default
         * provider URI.
         */
        // Gets the intent that started this Activity.
        Intent intent = getIntent();

        // If there is no data associated with the Intent, sets the data to the default URI, which
        // accesses a list of notes.
        if (intent.getData() == null) {
            intent.setData(NotePad.Notes.CONTENT_URI);
        }

        /*
         * Sets the callback for context menu activation for the ListView. The listener is set
         * to be this Activity. The effect is that context menus are enabled for items in the
         * ListView, and the context menu is handled by a method in NotesList.
         */
        getListView().setOnCreateContextMenuListener(this);
        ListView listView = getListView();
        // 整体背景改成浅色
        listView.setBackgroundColor(getResources().getColor(R.color.colorBackground));
        // 去掉默认的分割线
        listView.setDivider(null);
        listView.setDividerHeight(0);
        // 为了让第一个/最后一个 item 不贴边
        int padding = (int) (getResources().getDisplayMetrics().density * 8); // 8dp
        listView.setPadding(0, padding, 0, padding);
        listView.setClipToPadding(false);
        /* Performs a managed query. The Activity handles closing and requerying the cursor
         * when needed.
         *
         * Please see the introductory note about performing provider operations on the UI thread.
         */
        Cursor cursor = managedQuery(
            getIntent().getData(),            // Use the default content URI for the provider.
            PROJECTION,                       // Return the note ID and title for each note.
            null,                             // No where clause, return all records.
            null,                             // No where clause, therefore no where column values.
            NotePad.Notes.DEFAULT_SORT_ORDER  // Use the default sort order.
        );

        /*
         * The following two arrays create a "map" between columns in the cursor and view IDs
         * for items in the ListView. Each element in the dataColumns array represents
         * a column name; each element in the viewID array represents the ID of a View.
         * The SimpleCursorAdapter maps them in ascending order to determine where each column
         * value will appear in the ListView.
         */

        // The names of the cursor columns to display in the view, initialized to the title column
        // Cursor 中要拿来展示的两列：标题 + 修改时间
        String[] dataColumns = {
                NotePad.Notes.COLUMN_NAME_TITLE,
                NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE
        };

// 显示这两列的控件 id：标题 -> text1，时间 -> text2
        int[] viewIDs = {
                android.R.id.text1,
                android.R.id.text2
        };

        mAdapter = new SimpleCursorAdapter(
                this,
                R.layout.noteslist_item,
                cursor,
                dataColumns,
                viewIDs
        );
        mAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
            @Override
            public boolean setViewValue(View view, Cursor cursor, int columnIndex) {

                // 处理标题（这里顺便把分类和待办状态一起显示）
                if (columnIndex == COLUMN_INDEX_TITLE) {
                    String title = cursor.getString(COLUMN_INDEX_TITLE);
                    String category = cursor.getString(COLUMN_INDEX_CATEGORY);
                    int isTodo = cursor.getInt(COLUMN_INDEX_IS_TODO);
                    int isDone = cursor.getInt(COLUMN_INDEX_IS_DONE);

                    // 分类前缀
                    if (category != null && category.length() > 0) {
                        title = "[" + category + "] " + title;
                    }

                    // 待办前缀：未完成用 •，已完成用 ✓
                    if (isTodo == 1) {
                        if (isDone == 1) {
                            title = "✓ " + title;
                        } else {
                            title = "• " + title;
                        }
                    }

                    TextView tv = (TextView) view;
                    tv.setText(title);

                    // 已完成的待办加删除线
                    if (isTodo == 1 && isDone == 1) {
                        tv.setPaintFlags(tv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                    } else {
                        tv.setPaintFlags(tv.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                    }

                    return true;
                }

                // 处理修改时间
                if (columnIndex == COLUMN_INDEX_MODIFICATION_DATE) {
                    long time = cursor.getLong(COLUMN_INDEX_MODIFICATION_DATE);
                    CharSequence text = DateFormat.format("yyyy-MM-dd HH:mm", time);
                    ((TextView) view).setText(text);
                    return true;
                }

                // 其它列走默认逻辑
                return false;
            }
        });

        // Sets the ListView's adapter to be the cursor adapter that was just created.
        setListAdapter(mAdapter);
    }

    /**
     * Called when the user clicks the device's Menu button the first time for
     * this Activity. Android passes in a Menu object that is populated with items.
     *
     * Sets up a menu that provides the Insert option plus a list of alternative actions for
     * this Activity. Other applications that want to handle notes can "register" themselves in
     * Android by providing an intent filter that includes the category ALTERNATIVE and the
     * mimeTYpe NotePad.Notes.CONTENT_TYPE. If they do this, the code in onCreateOptionsMenu()
     * will add the Activity that contains the intent filter to its list of options. In effect,
     * the menu will offer the user other applications that can handle notes.
     * @param menu A Menu object, to which menu items should be added.
     * @return True, always. The menu should be displayed.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_options_menu, menu);

        // 保留原来的 intent alternatives 逻辑（unchanged）
        Intent intent = new Intent(null, getIntent().getData());
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);

        // ===== 使用系统 SearchView 的监听实现 =====
        MenuItem searchItem = menu.findItem(R.id.menu_search);
        if (searchItem != null) {
            // 先尝试从 MenuItem 拿到现成的 action view
            SearchView tmp = (SearchView) searchItem.getActionView();

            // 如果为空就创建一个新的 SearchView 并设置到 MenuItem 上
            if (tmp == null) {
                tmp = new SearchView(this);
                searchItem.setActionView(tmp);
            }

            // 把最终使用的 SearchView 存进 final 变量，供匿名类访问
            final SearchView searchViewFinal = tmp;

            searchViewFinal.setQueryHint(getString(R.string.menu_search));

            searchViewFinal.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    applyFilters(mCurrentCategoryFilter, query);
                    searchViewFinal.clearFocus();
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    applyFilters(mCurrentCategoryFilter, newText);
                    return true;
                }
            });

            // 当搜索框折叠关闭时清空查询条件（恢复为仅按分类或全部）
            searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                @Override
                public boolean onMenuItemActionExpand(MenuItem item) {
                    return true;
                }

                @Override
                public boolean onMenuItemActionCollapse(MenuItem item) {
                    applyFilters(mCurrentCategoryFilter, null);
                    return true;
                }
            });
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        // The paste menu item is enabled if there is data on the clipboard.
        ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);


        MenuItem mPasteItem = menu.findItem(R.id.menu_paste);

        // If the clipboard contains an item, enables the Paste option on the menu.
        if (clipboard.hasPrimaryClip()) {
            mPasteItem.setEnabled(true);
        } else {
            // If the clipboard is empty, disables the menu's Paste option.
            mPasteItem.setEnabled(false);
        }

        // Gets the number of notes currently being displayed.
        final boolean haveItems = getListAdapter().getCount() > 0;

        // If there are any notes in the list (which implies that one of
        // them is selected), then we need to generate the actions that
        // can be performed on the current selection.  This will be a combination
        // of our own specific actions along with any extensions that can be
        // found.
        if (haveItems) {

            // This is the selected item.
            Uri uri = ContentUris.withAppendedId(getIntent().getData(), getSelectedItemId());

            // Creates an array of Intents with one element. This will be used to send an Intent
            // based on the selected menu item.
            Intent[] specifics = new Intent[1];

            // Sets the Intent in the array to be an EDIT action on the URI of the selected note.
            specifics[0] = new Intent(Intent.ACTION_EDIT, uri);

            // Creates an array of menu items with one element. This will contain the EDIT option.
            MenuItem[] items = new MenuItem[1];

            // Creates an Intent with no specific action, using the URI of the selected note.
            Intent intent = new Intent(null, uri);

            /* Adds the category ALTERNATIVE to the Intent, with the note ID URI as its
             * data. This prepares the Intent as a place to group alternative options in the
             * menu.
             */
            intent.addCategory(Intent.CATEGORY_ALTERNATIVE);

            /*
             * Add alternatives to the menu
             */
            menu.addIntentOptions(
                Menu.CATEGORY_ALTERNATIVE,  // Add the Intents as options in the alternatives group.
                Menu.NONE,                  // A unique item ID is not required.
                Menu.NONE,                  // The alternatives don't need to be in order.
                null,                       // The caller's name is not excluded from the group.
                specifics,                  // These specific options must appear first.
                intent,                     // These Intent objects map to the options in specifics.
                Menu.NONE,                  // No flags are required.
                items                       // The menu items generated from the specifics-to-
                                            // Intents mapping
            );
                // If the Edit menu item exists, adds shortcuts for it.
                if (items[0] != null) {

                    // Sets the Edit menu item shortcut to numeric "1", letter "e"
                    items[0].setShortcut('1', 'e');
                }
            } else {
                // If the list is empty, removes any existing alternative actions from the menu
                menu.removeGroup(Menu.CATEGORY_ALTERNATIVE);
            }

        // Displays the menu
        return true;
    }

    /**
     * This method is called when the user selects an option from the menu, but no item
     * in the list is selected. If the option was INSERT, then a new Intent is sent out with action
     * ACTION_INSERT. The data from the incoming Intent is put into the new Intent. In effect,
     * this triggers the NoteEditor activity in the NotePad application.
     *
     * If the item was not INSERT, then most likely it was an alternative option from another
     * application. The parent method is called to process the item.
     * @param item The menu item that was selected by the user
     * @return True, if the INSERT menu item was selected; otherwise, the result of calling
     * the parent method.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_add) {
            startActivity(new Intent(Intent.ACTION_INSERT, getIntent().getData()));
            return true;

        } else if (id == R.id.menu_paste) {
            startActivity(new Intent(Intent.ACTION_PASTE, getIntent().getData()));
            return true;

        } else if (id == R.id.menu_filter_category) {
            // 弹出分类筛选对话框
            showFilterDialog();
            return true;

        } else if (id == R.id.menu_show_all) {
            mCurrentTodoFilter = TODO_FILTER_ALL;
            applyFilters(mCurrentCategoryFilter, mCurrentQuery);
            return true;

        } else if (id == R.id.menu_show_todo) {
            mCurrentTodoFilter = TODO_FILTER_ONLY_TODO;
            applyFilters(mCurrentCategoryFilter, mCurrentQuery);
            return true;

        } else if (id == R.id.menu_show_done) {
            mCurrentTodoFilter = TODO_FILTER_ONLY_DONE;
            applyFilters(mCurrentCategoryFilter, mCurrentQuery);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * This method is called when the user context-clicks a note in the list. NotesList registers
     * itself as the handler for context menus in its ListView (this is done in onCreate()).
     *
     * The only available options are COPY and DELETE.
     *
     * Context-click is equivalent to long-press.
     *
     * @param menu A ContexMenu object to which items should be added.
     * @param view The View for which the context menu is being constructed.
     * @param menuInfo Data associated with view.
     * @throws ClassCastException
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {

        // The data from the menu item.
        AdapterView.AdapterContextMenuInfo info;

        try {
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return;
        }

        Cursor cursor = (Cursor) getListAdapter().getItem(info.position);
        if (cursor == null) {
            return;
        }

        // Inflate menu from XML resource
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_context_menu, menu);

        // 根据当前行的待办状态，动态修改菜单文字/可见性
        int isTodo = cursor.getInt(COLUMN_INDEX_IS_TODO);
        int isDone = cursor.getInt(COLUMN_INDEX_IS_DONE);

        MenuItem todoItem = menu.findItem(R.id.context_toggle_todo);
        MenuItem doneItem = menu.findItem(R.id.context_toggle_done);

        if (todoItem != null) {
            todoItem.setTitle(isTodo == 1 ? R.string.menu_unset_todo : R.string.menu_set_todo);
        }
        if (doneItem != null) {
            if (isTodo == 1) {
                doneItem.setVisible(true);
                doneItem.setTitle(isDone == 1 ? R.string.menu_mark_undone : R.string.menu_mark_done);
            } else {
                doneItem.setVisible(false);
            }
        }

        // Sets the menu header to be the title of the selected note.
        menu.setHeaderTitle(cursor.getString(COLUMN_INDEX_TITLE));

        Intent intent = new Intent(null, Uri.withAppendedPath(getIntent().getData(),
                Integer.toString((int) info.id)));
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);
    }

    /**
     * 统一应用分类过滤 + 搜索关键字过滤（category == null 表示全部，query == null 或 "" 表示不按关键字过滤）
     */
    /**
     * 统一应用分类过滤 + 搜索关键字过滤 + 待办状态过滤
     * category == null 表示全部，query == null 或 "" 表示不按关键字过滤，
     * mCurrentTodoFilter 决定是否只看待办/已完成。
     */
    private void applyFilters(String category, String query) {
        mCurrentCategoryFilter = category;
        mCurrentQuery = (query != null && query.trim().length() > 0) ? query.trim() : null;

        String selection = null;
        String[] selectionArgs = null;

        ArrayList<String> parts = new ArrayList<>();
        ArrayList<String> args = new ArrayList<>();

        // 分类
        if (mCurrentCategoryFilter != null) {
            parts.add(NotePad.Notes.COLUMN_NAME_CATEGORY + "=?");
            args.add(mCurrentCategoryFilter);
        }

        // 搜索关键字（标题 + 正文）
        if (mCurrentQuery != null) {
            parts.add("(" + NotePad.Notes.COLUMN_NAME_TITLE + " LIKE ? OR "
                    + NotePad.Notes.COLUMN_NAME_NOTE + " LIKE ?)");
            String like = "%" + mCurrentQuery + "%";
            args.add(like);
            args.add(like);
        }

        // 待办状态
        if (mCurrentTodoFilter == TODO_FILTER_ONLY_TODO) {
            parts.add(NotePad.Notes.COLUMN_NAME_IS_TODO + "=?");
            parts.add(NotePad.Notes.COLUMN_NAME_IS_DONE + "=?");
            args.add("1");
            args.add("0");
        } else if (mCurrentTodoFilter == TODO_FILTER_ONLY_DONE) {
            parts.add(NotePad.Notes.COLUMN_NAME_IS_TODO + "=?");
            parts.add(NotePad.Notes.COLUMN_NAME_IS_DONE + "=?");
            args.add("1");
            args.add("1");
        }

        if (!parts.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.size(); i++) {
                if (i > 0) sb.append(" AND ");
                sb.append(parts.get(i));
            }
            selection = sb.toString();
            selectionArgs = args.toArray(new String[0]);
        }

        Cursor cursor = managedQuery(
                getIntent().getData(),
                PROJECTION,
                selection,
                selectionArgs,
                NotePad.Notes.DEFAULT_SORT_ORDER
        );

        if (mAdapter != null) {
            mAdapter.changeCursor(cursor);
        } else {
            mAdapter = new SimpleCursorAdapter(this, R.layout.noteslist_item, cursor,
                    new String[]{NotePad.Notes.COLUMN_NAME_TITLE, NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE},
                    new int[]{android.R.id.text1, android.R.id.text2});
            setListAdapter(mAdapter);
        }
    }

    /**
     * This method is called when the user selects an item from the context menu
     * (see onCreateContextMenu()). The only menu items that are actually handled are DELETE and
     * COPY. Anything else is an alternative option, for which default handling should be done.
     *
     * @param item The selected menu item
     * @return True if the menu item was DELETE, and no default processing is need, otherwise false,
     * which triggers the default handling of the item.
     * @throws ClassCastException
     */

    /**
     * 根据分类过滤刷新列表，category 为 null 表示显示全部
     */
    /**
     * 只改变分类筛选，然后统一走 applyFilters，保证和搜索/待办逻辑一致
     */
    void applyCategoryFilter(String category) {
        mCurrentCategoryFilter = category;
        applyFilters(mCurrentCategoryFilter, mCurrentQuery);
    }

    /**
     * 弹出“按分类查看”的对话框
     */
    private void showFilterDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_filter_category)
                .setItems(R.array.note_categories_filter, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String[] cats = getResources().getStringArray(R.array.note_categories_filter);
                        if (which == 0) {
                            // 选择“全部”
                            applyCategoryFilter(null);
                        } else {
                            // 按选择的分类过滤
                            String category = cats[which];
                            applyCategoryFilter(category);
                        }
                    }
                })
                .show();
    }

    /**
     * 弹出分类选择对话框，并把选中的分类写入数据库，然后刷新列表
     */
    private void showCategoryDialog(final Uri noteUri) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_set_category)
                .setItems(R.array.note_categories, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 取出选中的分类文字
                        String[] categories = getResources().getStringArray(R.array.note_categories);
                        String category = categories[which];

                        // 要更新的字段
                        ContentValues values = new ContentValues();
                        values.put(NotePad.Notes.COLUMN_NAME_CATEGORY, category);

                        // 写回数据库
                        getContentResolver().update(noteUri, values, null, null);

                        // 重新查询，刷新列表
                        Cursor cursor = managedQuery(
                                getIntent().getData(),
                                PROJECTION,
                                null,
                                null,
                                NotePad.Notes.DEFAULT_SORT_ORDER);

                        ((SimpleCursorAdapter) getListAdapter()).changeCursor(cursor);
                    }
                })
                .show();
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        // The data from the menu item.
        AdapterView.AdapterContextMenuInfo info;

        try {
            // Casts the data object in the item into the type for AdapterView objects.
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {

            // If the object can't be cast, logs an error
            Log.e(TAG, "bad menuInfo", e);

            // Triggers default processing of the menu item.
            return false;
        }

        // Gets the URI of the note that was long-pressed.
        Uri noteUri = ContentUris.withAppendedId(getIntent().getData(), info.id);

        // Gets the menu item's ID and compares it to known actions.
        int id = item.getItemId();

        if (id == R.id.context_open) {
            // Launch activity to view/edit the currently selected item
            startActivity(new Intent(Intent.ACTION_EDIT, noteUri));
            return true;

        } else if (id == R.id.context_copy) {   // 复制
            ClipboardManager clipboard = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);

            clipboard.setPrimaryClip(ClipData.newUri(
                    getContentResolver(),
                    "Note",
                    noteUri));

            return true;

        } else if (id == R.id.context_set_category) {   // 设置分类
            showCategoryDialog(noteUri);
            return true;

        } else if (id == R.id.context_toggle_todo) {    // 设为/取消待办
            Cursor c = (Cursor) getListAdapter().getItem(info.position);
            if (c != null) {
                int isTodo = c.getInt(COLUMN_INDEX_IS_TODO);
                ContentValues values = new ContentValues();
                if (isTodo == 1) {
                    // 取消待办，同时清空完成状态
                    values.put(NotePad.Notes.COLUMN_NAME_IS_TODO, 0);
                    values.put(NotePad.Notes.COLUMN_NAME_IS_DONE, 0);
                } else {
                    // 设为待办，默认未完成
                    values.put(NotePad.Notes.COLUMN_NAME_IS_TODO, 1);
                    values.put(NotePad.Notes.COLUMN_NAME_IS_DONE, 0);
                }
                getContentResolver().update(noteUri, values, null, null);
                applyFilters(mCurrentCategoryFilter, mCurrentQuery);
            }
            return true;

        } else if (id == R.id.context_toggle_done) {    // 标记完成/未完成
            Cursor c = (Cursor) getListAdapter().getItem(info.position);
            if (c != null) {
                int isDone = c.getInt(COLUMN_INDEX_IS_DONE);
                ContentValues values = new ContentValues();
                // 只要点了这个按钮，一定是待办
                values.put(NotePad.Notes.COLUMN_NAME_IS_TODO, 1);
                values.put(NotePad.Notes.COLUMN_NAME_IS_DONE, isDone == 1 ? 0 : 1);
                getContentResolver().update(noteUri, values, null, null);
                applyFilters(mCurrentCategoryFilter, mCurrentQuery);
            }
            return true;

        } else if (id == R.id.context_delete) {         // 删除
            // Deletes the note from the provider by passing in a URI in note ID format.
            getContentResolver().delete(
                    noteUri,  // The URI of the provider
                    null,     // No where clause is needed, since only a single note ID is being passed in.
                    null      // No where clause is used, so no where arguments are needed.
            );
            return true;
        }

        return super.onContextItemSelected(item);
    }

    /**
     * This method is called when the user clicks a note in the displayed list.
     *
     * This method handles incoming actions of either PICK (get data from the provider) or
     * GET_CONTENT (get or create data). If the incoming action is EDIT, this method sends a
     * new Intent to start NoteEditor.
     * @param l The ListView that contains the clicked item
     * @param v The View of the individual item
     * @param position The position of v in the displayed list
     * @param id The row ID of the clicked item
     */
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {

        // Constructs a new URI from the incoming URI and the row ID
        Uri uri = ContentUris.withAppendedId(getIntent().getData(), id);

        // Gets the action from the incoming Intent
        String action = getIntent().getAction();

        // Handles requests for note data
        if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {

            // Sets the result to return to the component that called this Activity. The
            // result contains the new URI
            setResult(RESULT_OK, new Intent().setData(uri));
        } else {

            // Sends out an Intent to start an Activity that can handle ACTION_EDIT. The
            // Intent's data is the note ID URI. The effect is to call NoteEdit.
            startActivity(new Intent(Intent.ACTION_EDIT, uri));
        }
    }
}
