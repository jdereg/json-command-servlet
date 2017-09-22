package com.cedarsoftware.servlet

import groovy.transform.CompileStatic
import org.junit.Test

import java.awt.Point
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicLong

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

    @Test
    void testAllToStringArray()
    {
        final Method method = methodWithStringArray
        Object[] args = ConfigurationProvider.convertArgs(method, [['foo','bar']] as Object[])
        assert args[0] instanceof String[]
        String[] strings = (String[]) args[0]
        assert strings.length == 2
        assert strings[0] == 'foo'
        assert strings[1] == 'bar'

        args = ConfigurationProvider.convertArgs(method, [['foo','bar'] as Object[]] as Object[])
        assert args[0] instanceof String[]
        strings = (String[]) args[0]
        assert strings.length == 2
        assert strings[0] == 'foo'
        assert strings[1] == 'bar'

        args = ConfigurationProvider.convertArgs(method, [['foo','bar'] as Set] as Object[])
        assert args[0] instanceof String[]
        strings = (String[]) args[0]
        assert strings.length == 2
        assert strings[0] == 'foo'
        assert strings[1] == 'bar'
    }

    @Test
    void testManyTypesToString()
    {
        final Method method = methodWithStringArg
        Object[] args = ConfigurationProvider.convertArgs(method, [75] as Object[])
        assert args[0] instanceof String
        String x = args[0]
        assert x == '75'

        AtomicLong atomicLong = new AtomicLong(69)
        args = ConfigurationProvider.convertArgs(method, [atomicLong] as Object[])
        assert args[0] instanceof String
        x = args[0]
        assert x == '69'

        args = ConfigurationProvider.convertArgs(method, [null] as Object[])
        assert args[0] == null
    }

    @Test
    void testMapSupport()
    {
        final Method method = methodThatTakesMap
        Object[] args = ConfigurationProvider.convertArgs(method, [[foo:'bar'] as LinkedHashMap] as Object[])
        assert args[0] instanceof Map
        Map map = args[0] as Map
        assert map.size() == 1
        assert map.foo == 'bar'

        args = ConfigurationProvider.convertArgs(method, [new Point(2, 5)] as Object[])
        assert args[0] instanceof Map
        map = args[0] as Map
        assert map.size() == 2
        assert map.x == 2
        assert map.y == 5
    }

    @Test
    void testMapToSpecificType()
    {
        final Method method = methodThatTakesPoint
        Object[] args = ConfigurationProvider.convertArgs(method, [[x: 2, y: 5] as LinkedHashMap] as Object[])
        assert args[0] instanceof Point
        Point point = args[0] as Point
        assert point.x == 2
        assert point.y == 5

        args = ConfigurationProvider.convertArgs(method, [new Point(2, 5)] as Object[])
        assert args[0] instanceof Point
        point = args[0] as Point
        assert point.x == 2
        assert point.y == 5
    }

    Method getSetMethod()
    {
        return ConfigurationProvider.getMethod(TestArgumentMarshalling.class, 'methodWithSetArg', 3)
    }
    
    void methodWithSetArg(Set foo, List bar, Object[] baz)
    {
    }

    Method getMethodWithStringArray()
    {
        return ConfigurationProvider.getMethod(TestArgumentMarshalling.class, 'methodWithStringArray', 1)
    }

    void methodWithStringArray(String[] strings)
    {
    }

    Method getMethodWithStringArg()
    {
        return ConfigurationProvider.getMethod(TestArgumentMarshalling.class, 'methodTakesAString', 1)
    }

    void methodTakesAString(String foo)
    {
    }

    Method getMethodThatTakesMap()
    {
        return ConfigurationProvider.getMethod(TestArgumentMarshalling.class, 'methodTakesMap', 1)
    }

    void methodTakesMap(Map foo)
    {
    }

    Method getMethodThatTakesPoint()
    {
        return ConfigurationProvider.getMethod(TestArgumentMarshalling.class, 'methodTakesPoint', 1)
    }

    void methodTakesPoint(Point point)
    {
    }
}
