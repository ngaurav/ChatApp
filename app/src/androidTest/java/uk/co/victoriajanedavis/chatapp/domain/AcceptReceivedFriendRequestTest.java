package uk.co.victoriajanedavis.chatapp.domain;

import android.arch.core.executor.testing.InstantTaskExecutorRule;
import android.arch.persistence.room.Room;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;

import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import uk.co.victoriajanedavis.chatapp.data.mappers.UserNwFriendshipDbMapper;
import uk.co.victoriajanedavis.chatapp.data.model.db.FriendshipDbModel;
import uk.co.victoriajanedavis.chatapp.data.model.network.UserNwModel;
import uk.co.victoriajanedavis.chatapp.data.repositories.ReceivedFriendRequestRepository;
import uk.co.victoriajanedavis.chatapp.data.repositories.cache.ReceivedFriendRequestCache;
import uk.co.victoriajanedavis.chatapp.data.repositories.store.BaseReactiveStore;
import uk.co.victoriajanedavis.chatapp.data.room.ChatAppDatabase;
import uk.co.victoriajanedavis.chatapp.data.services.ChatAppService;
import uk.co.victoriajanedavis.chatapp.domain.Cache;
import uk.co.victoriajanedavis.chatapp.domain.entities.FriendshipEntity;
import uk.co.victoriajanedavis.chatapp.domain.interactors.AcceptReceivedFriendRequest;
import uk.co.victoriajanedavis.chatapp.test_common.BaseTest;
import uk.co.victoriajanedavis.chatapp.test_common.ModelGenerationUtil;

@RunWith(AndroidJUnit4.class)
public class AcceptReceivedFriendRequestTest extends BaseTest {

    private ChatAppDatabase database;
    private BaseReactiveStore<FriendshipDbModel> friendshipStore;
    private ReceivedFriendRequestRepository repository;
    private AcceptReceivedFriendRequest interactor;

    private UserNwFriendshipDbMapper nwDbMapper = new UserNwFriendshipDbMapper(false, false);

    @Mock
    private ChatAppService service;


    @Before
    public void setUp() {
        database = Room.inMemoryDatabaseBuilder(
                InstrumentationRegistry.getContext(),
                ChatAppDatabase.class)
                // allowing main thread queries, just for testing
                .allowMainThreadQueries()
                .build();

        Cache.DiskCache<UUID, FriendshipDbModel> friendshipCache = new ReceivedFriendRequestCache(database);
        friendshipStore = new BaseReactiveStore<>(friendshipCache);

        repository = new ReceivedFriendRequestRepository(friendshipStore, service);

        interactor = new AcceptReceivedFriendRequest(repository);
    }

    @After
    public void closeDb() {
        database.close();
    }

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();


    @Test
    public void acceptingFriendRequestReturnsUserFromNetworkAndRemovesFromGetAllStream() {
        UserNwModel userNwModel = ModelGenerationUtil.createUserNwModel("rickardo");
        new ArrangeBuilder().withAcceptedFriendRequestFromService(userNwModel);
        friendshipStore.storeSingular(nwDbMapper.mapFrom(userNwModel)).subscribe();

        TestObserver<List<FriendshipEntity>> getAllObserver = repository.getAllReceivedFriendRequests().test();
        TestObserver<FriendshipEntity> acceptObserver = interactor.getSingle("rickardo").test();

        getAllObserver.assertValueAt(0, list -> list.size() == 1);
        getAllObserver.assertValueAt(0, list -> list.get(0).getUuid().equals(userNwModel.getUuid()));
        getAllObserver.assertValueAt(1, List::isEmpty);
        getAllObserver.assertValueCount(2);
        getAllObserver.assertNotComplete();

        acceptObserver.assertValueAt(0, userEntity -> userEntity.getUuid().equals(userNwModel.getUuid()));
        acceptObserver.assertValueCount(1);
        acceptObserver.assertComplete();
    }

    @Test
    public void acceptFriendRequestSingleEmitsErrorWhenNetworkServiceErrors() {
        UserNwModel userNwModel = ModelGenerationUtil.createUserNwModel("rickardo");
        Throwable throwable = Mockito.mock(Throwable.class);
        new ArrangeBuilder().withErrorInAcceptFriendRequestFromService(throwable);

        TestObserver<FriendshipEntity> acceptObserver = interactor.getSingle(userNwModel.getUsername()).test();

        acceptObserver.assertError(throwable);
    }


    /****************************************************/
    /****************** Helper methods ******************/
    /****************************************************/

    private class ArrangeBuilder {

        private ArrangeBuilder withAcceptedFriendRequestFromService(UserNwModel userNwModel) {
            Mockito.when(service.acceptFriendRequest(userNwModel.getUsername())).thenReturn(Single.just(userNwModel));
            return this;
        }

        private ArrangeBuilder withErrorInAcceptFriendRequestFromService(Throwable error) {
            Mockito.when(service.acceptFriendRequest(Mockito.anyString())).thenReturn(Single.error(error));
            return this;
        }

    }
}
