package com.cedarsoftware.servlet

import groovy.transform.CompileStatic
import org.junit.Test

import java.lang.reflect.Method

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br/>
 *         Copyright (c) Cedar Software LLC
 *         <br/><br/>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br/><br/>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br/><br/>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
@CompileStatic
class TestArgumentMarshalling
{
    @Test
    void testListToAll()
    {
        final Method method = setMethod
        Object[] args = ConfigurationProvider.convertArgs(method, [[1, 2, 2, 3], [1, 2, 2, 3], [1, 2, 2, 3]] as Object[])
        assert args[0] instanceof Set
        Set set = args[0] as Set
        assert set.first() == 1   // maintained order
        assert set.size() == 3
        assert set.contains(2)
        assert set.contains(3)
        assert set.last() == 3  // maintained order

        assert args[1] instanceof Collection
        Collection col = args[1] as Collection
        assert col.first() == 1   // maintained order
        assert col.size() == 4
        assert col.contains(2)
        assert col.contains(3)
        assert col.last() == 3  // maintained order

        assert args[2] instanceof Object[]
        Object[] array = args[2] as Object[]
        assert array.length == 4
        assert array[0] == 1   // maintained order
        assert array[1] == 2   // maintained order
        assert array[2] == 2   // maintained order
        assert array[3] == 3   // maintained order
    }

    @Test
    void testArrayToAll()
    {
        final Method method = setMethod
        Object[] args = ConfigurationProvider.convertArgs(method, [[1, 2, 2, 3] as Object[], [1, 2, 2, 3] as Object[], [1, 2, 2, 3] as Object[]] as Object[])
        assert args[0] instanceof Set
        Set set = args[0] as Set
        assert set.first() == 1   // maintained order
        assert set.size() == 3
        assert set.contains(2)
        assert set.contains(3)
        assert set.last() == 3  // maintained order
        
        assert args[1] instanceof Collection
        Collection col = args[1] as Collection
        assert col.first() == 1   // maintained order
        assert col.size() == 4
        assert col.contains(2)
        assert col.contains(3)
        assert col.last() == 3  // maintained order

        assert args[2] instanceof Object[]
        Object[] array = args[2] as Object[]
        assert array.length == 4
        assert array[0] == 1   // maintained order
        assert array[1] == 2   // maintained order
        assert array[2] == 2   // maintained order
        assert array[3] == 3   // maintained order
    }

    @Test
    void testSetToAll()
    {
        final Method method = setMethod
        Object[] args = ConfigurationProvider.convertArgs(method, [[1, 2, 3] as Set, [1, 2, 3] as Set, [1, 2, 3] as Set] as Object[])
        assert args[0] instanceof Set
        Set set = args[0] as Set
        assert set.first() == 1   // maintained order
        assert set.size() == 3
        assert set.contains(2)
        assert set.contains(3)
        assert set.last() == 3  // maintained order

        assert args[1] instanceof Collection
        Collection col = args[1] as Collection
        assert col.first() == 1   // maintained order
        assert col.size() == 3
        assert col.contains(2)
        assert col.contains(3)
        assert col.last() == 3  // maintained order

        assert args[2] instanceof Object[]
        Object[] array = args[2] as Object[]
        assert array.length == 3
        assert array[0] == 1   // maintained order
        assert array[1] == 2   // maintained order
        assert array[2] == 3   // maintained order
    }

    Method getSetMethod()
    {
        return ConfigurationProvider.getMethod(TestArgumentMarshalling.class, 'methodWithSetArg', 3)
    }
    
    void methodWithSetArg(Set foo, List bar, Object[] baz)
    {
    }
}
