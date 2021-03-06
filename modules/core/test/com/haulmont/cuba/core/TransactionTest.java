/*
 * Copyright (c) 2008-2016 Haulmont.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.haulmont.cuba.core;

import com.haulmont.bali.db.QueryRunner;
import com.haulmont.cuba.core.entity.Server;
import com.haulmont.cuba.security.entity.User;
import com.haulmont.cuba.testsupport.TestContainer;
import com.haulmont.cuba.testsupport.TestSupport;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

public class TransactionTest {

    @ClassRule
    public static TestContainer cont = TestContainer.Common.INSTANCE;

    private static final String TEST_EXCEPTION_MSG = "test exception";

    private Persistence persistence;

    @Before
    public void setUp() throws Exception {
        persistence = cont.persistence();

        QueryRunner runner = new QueryRunner(cont.persistence().getDataSource());
        runner.update("delete from SYS_SERVER");
    }

    @Test
    public void testNoTransaction() {
        try {
            EntityManager em = cont.persistence().getEntityManager();
            fail();
        } catch (Exception e) {
            assertTrue(e instanceof IllegalStateException);
        }
    }

    @Test
    public void testCommit() {
        UUID id;
        Transaction tx = cont.persistence().createTransaction();
        try {
            EntityManager em = cont.persistence().getEntityManager();
            assertNotNull(em);
            Server server = new Server();
            id = server.getId();
            server.setName("localhost");
            server.setRunning(true);
            em.persist(server);

            tx.commit();
        } finally {
            tx.end();
        }

        tx = cont.persistence().createTransaction();
        try {
            EntityManager em = cont.persistence().getEntityManager();
            Server server = em.find(Server.class, id);
            assertEquals(id, server.getId());
            server.setRunning(false);

            tx.commit();
        } finally {
            tx.end();
        }
    }

    @Test
    public void testCommitRetaining() {
        UUID id;
        Transaction tx = cont.persistence().createTransaction();
        try {
            EntityManager em = cont.persistence().getEntityManager();
            assertNotNull(em);
            Server server = new Server();
            id = server.getId();
            server.setName("localhost");
            server.setRunning(true);
            em.persist(server);

            tx.commitRetaining();

            em = cont.persistence().getEntityManager();
            server = em.find(Server.class, id);
            assertEquals(id, server.getId());
            server.setRunning(false);

            tx.commit();
        } finally {
            tx.end();
        }
    }

    @Test
    public void testRollback() {
        try {
            __testRollback();
            fail();
        } catch (Exception e) {
            assertEquals(TEST_EXCEPTION_MSG, e.getMessage());
        }
    }

    private void __testRollback() {
        Transaction tx = cont.persistence().createTransaction();
        try {
            EntityManager em = cont.persistence().getEntityManager();
            assertNotNull(em);
            Server server = new Server();
            server.setName("localhost");
            server.setRunning(true);
            em.persist(server);
            throwException();
            tx.commit();
        } finally {
            tx.end();
        }
    }

    @Test
    public void testRollbackAndCatch() {
        try {
            __testRollbackAndCatch();
            fail();
        } catch (Exception e) {
            assertEquals(TEST_EXCEPTION_MSG, e.getMessage());
        }
    }

    private void __testRollbackAndCatch() {
        Transaction tx = cont.persistence().createTransaction();
        try {
            EntityManager em = cont.persistence().getEntityManager();
            assertNotNull(em);
            Server server = new Server();
            server.setName("localhost");
            server.setRunning(true);
            em.persist(server);
            throwException();
            tx.commit();
        } catch (RuntimeException e) {
            System.out.println("Caught exception: " + e.getMessage());
            throw e;
        } finally {
            tx.end();
        }
    }

    @Test
    public void testCommitRetainingAndRollback() {
        try {
            __testCommitRetainingAndRollback();
            fail();
        } catch (Exception e) {
            assertEquals(TEST_EXCEPTION_MSG, e.getMessage());
        }
    }

    private void __testCommitRetainingAndRollback() {
        UUID id;
        Transaction tx = cont.persistence().createTransaction();
        try {
            EntityManager em = cont.persistence().getEntityManager();
            assertNotNull(em);
            Server server = new Server();
            id = server.getId();
            server.setName("localhost");
            server.setRunning(true);
            em.persist(server);

            tx.commitRetaining();

            em = cont.persistence().getEntityManager();
            server = em.find(Server.class, id);
            assertEquals(id, server.getId());
            server.setRunning(false);

            throwException();

            tx.commit();
        } finally {
            tx.end();
        }
    }

    @Test
    public void testNestedRollback() {
        try {
            Transaction tx = cont.persistence().createTransaction();
            try {

                Transaction tx1 = cont.persistence().getTransaction();
                try {
                    throwException();
                    fail();
                    tx1.commit();
                } catch (RuntimeException e) {
                    assertEquals(TEST_EXCEPTION_MSG, e.getMessage());
                } finally {
                    tx1.end();
                }

                tx.commit();
                fail();
            } finally {
                tx.end();
            }
        } catch (Exception e) {
            assertTrue(true);
        }
    }

    @Test
    public void testSuspend() {
        Transaction tx = cont.persistence().getTransaction();
        try {
            EntityManager em = cont.persistence().getEntityManager();
            Server server = new Server();
            server.setName("localhost");
            server.setRunning(true);
            em.persist(server);

            Transaction tx1 = cont.persistence().createTransaction();
            try {
                EntityManager em1 = cont.persistence().getEntityManager();
                assertTrue(em != em1);

                Query query = em1.createQuery("select s from sys$Server s");
                List list = query.getResultList();
                assertNotNull(list);

                tx1.commit();
            } finally {
                tx1.end();
            }

            tx.commit();
        } finally {
            tx.end();
        }
    }

    @Test
    public void testSuspendRollback() {
        Transaction tx = cont.persistence().createTransaction();
        try {
            EntityManager em = cont.persistence().getEntityManager();
            Server server = new Server();
            server.setName("localhost");
            server.setRunning(true);
            em.persist(server);

            Transaction tx1 = cont.persistence().createTransaction();
            try {
                EntityManager em1 = cont.persistence().getEntityManager();
                assertTrue(em != em1);
                Server server1 = em1.find(Server.class, server.getId());
                assertNull(server1);
                throwException();
                tx1.commit();
            } catch (Exception e) {
                //
            } finally {
                tx1.end();
            }

            tx.commit();
        } finally {
            tx.end();
        }
    }

    @Test
    public void testRunInTransaction() throws Exception {
        UUID id = cont.persistence().callInTransaction(em -> {
            assertNotNull(em);
            Server server = new Server();
            server.setName("localhost");
            server.setRunning(true);
            em.persist(server);
            return server.getId();
        });

        cont.persistence().runInTransaction(em -> {
            Server server = em.find(Server.class, id);
            assertNotNull(server);
            assertEquals(id, server.getId());
            server.setRunning(false);
        });

    }

    @Test
    public void testReadOnly() throws Exception {
        try (Transaction tx = persistence.createTransaction(new TransactionParams().setReadOnly(true))) {
            TypedQuery<User> query = persistence.getEntityManager().createQuery("select u from sec$User u where u.id = ?1", User.class);
            query.setParameter(1, TestSupport.ADMIN_USER_ID);
            User result = query.getSingleResult();
            tx.commit();
        }

        // read-only transaction cannot be committed if it contains changed entities
        UUID id = persistence.callInTransaction(em -> {
            Server server = new Server();
            server.setName("localhost");
            em.persist(server);
            return server.getId();
        });
        try (Transaction tx = persistence.createTransaction(new TransactionParams().setReadOnly(true))) {
            TypedQuery<Server> query = persistence.getEntityManager().createQuery("select e from sys$Server e where e.id = ?1", Server.class);
            query.setParameter(1, id);
            Server server = query.getSingleResult();
            server.setName("changed");
            try {
                tx.commit();
                fail();
            } catch (IllegalStateException e) {
                // ok
            }
        }
    }

    private void throwException() {
        throw new RuntimeException(TEST_EXCEPTION_MSG);
    }
}